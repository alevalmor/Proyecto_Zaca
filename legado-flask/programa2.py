"""Programa 2 · Administrador: el único que crea perfiles y accesos al Programa 1."""
import secrets
from functools import wraps

import psycopg
from flask import Flask, abort, flash, redirect, render_template, request, session, url_for
from psycopg.sql import SQL, Identifier, Literal

from db import (SERVIDORES, abrir_sesion, auditar, cerrar_sesion, conectar, credenciales, db, db_en, error_conexion,
                mensaje_bd, preparar, q, q1)
from reglas import PERMISOS_PERFIL, ROL_PERFIL, VISTAS, usuario_bd_valido

app = Flask(__name__)
preparar(app, 'login', 'usuarios')

TABLAS = ['responsable', 'centro_empaque', 'lote_produccion', 'control_calidad', 'vehiculo',
          'zona_liberacion', 'envio', 'liberacion']
POR_PAGINA = 20


def requiere_admin(f):
    @wraps(f)
    def envuelta(*args, **kwargs):
        if 'admin' not in session:
            return redirect(url_for('login'))
        credenciales()
        return f(*args, **kwargs)
    return envuelta


def en_ambos(orden):
    """Los roles no se replican: cada orden va a los dos servidores, primero a la otra sede para que,
    si no responde, no quede cambiado solo uno."""
    otra = 'EMPAQUE' if session['sede'] == 'PLANTA' else 'PLANTA'
    with db_en(otra) as con:
        con.execute(orden)
    db().execute(orden)


def clave_temporal():
    return 'Moscamed#' + secrets.token_urlsafe(6) + str(secrets.randbelow(10))


@app.route('/', methods=['GET', 'POST'])
def login():
    error = None
    sede = request.form.get('sede', 'PLANTA')
    if request.method == 'POST':
        usuario, clave = request.form.get('usuario', '').strip(), request.form.get('clave', '')
        if sede not in SERVIDORES:
            abort(400)
        try:
            with conectar(sede, usuario, clave) as con:
                es_admin = con.execute("SELECT pg_has_role(current_user, 'rol_admin', 'member') AS si").fetchone()['si']
        except psycopg.OperationalError as e:
            error = error_conexion(e, 'esa sede')
        else:
            if not es_admin:
                error = 'Este usuario no es administrador.'
            else:
                abrir_sesion(sede, usuario, clave, admin=True)
                return redirect(url_for('usuarios'))
    return render_template('admin/login.html', error=error, sede=sede, servidores=SERVIDORES)


@app.post('/salir')
def salir():
    cerrar_sesion()
    return redirect(url_for('login'))


@app.route('/usuarios')
@requiere_admin
def usuarios():
    f = {k: request.args.get(k, '') for k in ('q', 'sede', 'activo')}
    filtros, p = ['true'], dict(f)
    if f['q']:
        filtros.append("(nombre || ' ' || ap_paterno || ' ' || coalesce(ap_materno, '') ILIKE %(patron)s OR num_empleado ILIKE %(patron)s)")
        p['patron'] = f"%{f['q']}%"
    if f['sede']:
        filtros.append('sede = %(sede)s')
    if f['activo']:
        filtros.append('activo = %(activo)s')
    donde = SQL(' AND '.join(filtros))
    pagina = max(request.args.get('pagina', 1, type=int), 1)
    total = q1(SQL('SELECT count(*) AS n FROM responsable WHERE {}').format(donde), p)['n']
    filas = q(SQL('SELECT * FROM responsable WHERE {} ORDER BY activo DESC, sede DESC, num_empleado LIMIT %(lim)s OFFSET %(off)s').format(donde),
              {**p, 'lim': POR_PAGINA, 'off': (pagina - 1) * POR_PAGINA})
    resumen = q1("""SELECT count(*) FILTER (WHERE sede = 'PLANTA') AS pes, count(*) FILTER (WHERE sede = 'EMPAQUE') AS pem,
                           count(*) FILTER (WHERE activo) AS activos, count(*) FILTER (WHERE NOT activo) AS bajas FROM responsable""")
    return render_template('admin/usuarios.html', f=f, filas=filas, total=total, pagina=pagina, por=POR_PAGINA, r=resumen)


@app.route('/usuarios/nuevo', methods=['GET', 'POST'])
@requiere_admin
def nuevo_usuario():
    perfil = request.values.get('perfil', 'PES')
    if perfil not in PERMISOS_PERFIL:
        perfil = 'PES'
    sede = 'PLANTA' if perfil == 'PES' else 'EMPAQUE'
    prefijo = 'MET-' if perfil == 'PES' else 'TAP-'
    errores = []
    v = request.form
    if request.method == 'POST':
        usuario = v.get('usuario_bd', '').strip().lower()
        if not usuario_bd_valido(usuario):
            errores.append('El usuario va en minúsculas, empieza con letra y solo lleva letras, números, punto o guion bajo.')
        for campo, nombre in (('nombre', 'Nombre'), ('ap_paterno', 'Apellido paterno'), ('puesto', 'Puesto')):
            if not v.get(campo, '').strip():
                errores.append(f'Falta {nombre.lower()}.')
        if not errores:
            clave = clave_temporal()
            orden = SQL('CREATE ROLE {} LOGIN PASSWORD {} CONNECTION LIMIT 30 IN ROLE {}').format(
                Identifier(usuario), Literal(clave), Identifier(ROL_PERFIL[perfil]))
            try:
                en_ambos(orden)
            except psycopg.Error as e:
                errores.append('No se pudo crear el usuario en los dos servidores: ' + mensaje_bd(e))
            else:
                try:
                    with db().transaction():
                        n = q1('SELECT coalesce(max(substr(num_empleado, 5)::int), 0) + 1 AS n FROM responsable WHERE num_empleado LIKE %s',
                               (prefijo + '%',))['n']
                        empleado = f'{prefijo}{n:04d}'
                        rid = q1("""INSERT INTO responsable (num_empleado, nombre, ap_paterno, ap_materno, puesto, telefono, correo, sede, usuario_bd)
                                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id""",
                                 (empleado, v['nombre'].strip(), v['ap_paterno'].strip(), v.get('ap_materno', '').strip() or None,
                                  v['puesto'].strip(), v.get('telefono', '').strip() or None, v.get('correo', '').strip() or None,
                                  sede, usuario))['id']
                        # Solo se guardan las vistas donde el administrador quitó algo.
                        for vista, (ver, mod) in PERMISOS_PERFIL[perfil].items():
                            qv, qm = f'ver_{vista}' in v, f'mod_{vista}' in v
                            if (ver and not qv) or (mod and not qm):
                                db().execute('INSERT INTO permiso_vista VALUES (%s, %s, %s, %s)', (rid, vista, qv, qm))
                        auditar(db(), f'Creó el usuario {usuario} ({perfil})')
                except psycopg.Error as e:
                    errores.append(mensaje_bd(e) + ' El usuario de PostgreSQL ya se creó; bórralo con DROP ROLE antes de reintentar.')
                else:
                    flash(f'Usuario {usuario} creado ({empleado}). Contraseña temporal: {clave} — entrégasela en persona.')
                    return redirect(url_for('usuarios'))
    permisos = [(clave, nombre, PERMISOS_PERFIL[perfil][clave]) for clave, nombre in VISTAS]
    return render_template('admin/nuevo_usuario.html', perfil=perfil, sede=sede, prefijo=prefijo, rol=ROL_PERFIL[perfil],
                           permisos=permisos, v=v, errores=errores, enviado=request.method == 'POST')


@app.post('/usuarios/<int:rid>/<accion>')
@requiere_admin
def accion_usuario(rid, accion):
    u = q1('SELECT * FROM responsable WHERE id = %s', (rid,)) or abort(404)
    rol = Identifier(u['usuario_bd'])
    try:
        if accion == 'clave':
            clave = clave_temporal()
            en_ambos(SQL('ALTER ROLE {} PASSWORD {}').format(rol, Literal(clave)))
            auditar(db(), f"Restableció la contraseña de {u['usuario_bd']}")
            flash(f"Contraseña temporal de {u['usuario_bd']}: {clave} — entrégasela en persona.")
        elif accion in ('baja', 'reactivar'):
            activo = accion == 'reactivar'
            en_ambos(SQL('ALTER ROLE {} ' + ('LOGIN' if activo else 'NOLOGIN')).format(rol))
            db().execute('UPDATE responsable SET activo = %s WHERE id = %s', (activo, rid))
            auditar(db(), f"{'Reactivó' if activo else 'Dio de baja'} a {u['usuario_bd']}")
            flash(f"{u['usuario_bd']} {'reactivado' if activo else 'dado de baja'}.")
        else:
            abort(404)
    except psycopg.Error as e:
        flash(mensaje_bd(e))
    return redirect(request.referrer or url_for('usuarios'))


@app.route('/sistema')
@requiere_admin
def sistema():
    sedes = {}
    for sede, (host, puerto) in SERVIDORES.items():
        info = {'host': host, 'puerto': puerto, 'ok': False}
        local = sede == session['sede']
        try:
            con = db() if local else db_en(sede)
            info['publica'] = [r['pubname'] for r in con.execute('SELECT pubname FROM pg_publication')]
            info['suscripciones'] = con.execute(
                'SELECT subname, pid IS NOT NULL AS activa, latest_end_time FROM pg_stat_subscription WHERE relid IS NULL').fetchall()
            info['envia'] = con.execute(
                'SELECT application_name, pg_wal_lsn_diff(sent_lsn, replay_lsn) AS retraso FROM pg_stat_replication').fetchall()
            info['conteos'] = {t: con.execute(SQL('SELECT count(*) AS n FROM {}').format(Identifier(t))).fetchone()['n'] for t in TABLAS}
            try:
                info['pgpool'] = con.execute('SHOW pool_nodes').fetchall()
            except psycopg.Error:
                info['pgpool'] = None  # conectado directo a PostgreSQL, no por Pgpool-II
            try:
                info['trampas'] = con.execute('SELECT count(DISTINCT trampa) AS n FROM trampeo.revision').fetchone()['n']
            except psycopg.Error:
                info['trampas'] = None
            info['ok'] = True
            if not local:
                con.close()
        except psycopg.Error as e:
            info['error'] = str(e).splitlines()[0]
        sedes[sede] = info
    iguales = all(s['ok'] for s in sedes.values()) and sedes['PLANTA']['conteos'] == sedes['EMPAQUE']['conteos']
    bitacora = q('SELECT * FROM auditoria ORDER BY fecha DESC LIMIT 10')
    return render_template('admin/sistema.html', sedes=sedes, tablas=TABLAS, iguales=iguales, bitacora=bitacora)


if __name__ == '__main__':
    app.run(port=5001, debug=False)
