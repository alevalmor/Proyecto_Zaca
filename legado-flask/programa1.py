"""Programa 1 · Operativo: planta esterilizadora (PES) y centro de empaque (PEM)."""
import csv
import io
from collections import Counter
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from functools import wraps

import psycopg
from flask import Flask, Response, abort, flash, redirect, render_template, request, session, url_for
from psycopg.sql import SQL, Identifier, Literal

from db import (SERVIDORES, abrir_sesion, auditar, cambiar_clave_guardada, cerrar_sesion, conectar,
                credenciales, db, db_en, error_conexion, mensaje_bd, preparar, q, q1)
from reglas import (SEDE_PERFIL, VISTAS, contrasena_valida, entero, errores_envio, permisos, prioridad_zona,
                    sugeridas_semana)

app = Flask(__name__)
preparar(app, 'login', 'inicio')

MENU = [
    ('General', [('inicio', 'Inicio', 'inicio')]),
    ('Lotes', [('nuevo_lote', 'Nuevo lote', 'nuevo_lote'), ('lotes', 'Lista de lotes', 'lotes'),
               ('estado_lote', 'Estado de lote', 'estado_lote_ultimo')]),
    ('Envíos', [('nuevo_envio', 'Nuevo envío', 'nuevo_envio'), ('envios', 'Lista de envíos', 'envios'),
                ('estado_envio', 'Estado de envío', 'estado_envio_ultimo')]),
    ('Reportes', [('zonas', 'Zonas prioritarias', 'zonas')]),
    ('Ajustes', [('perfil', 'Mi perfil', 'perfil')]),
]
# Pantallas que marcan otra entrada del menú como activa.
ACTIVO = {'modificar_lote': 'lotes', 'modificar_envio': 'envios', 'estado_lote_ultimo': 'estado_lote',
          'estado_envio_ultimo': 'estado_envio'}
POR_PAGINA = 20


def puede(vista, modificar=False):
    if vista in ('inicio', 'perfil'):
        return True
    ver, mod = session['permisos'][vista]
    return mod if modificar else ver


def requiere(vista, modificar=False):
    def deco(f):
        @wraps(f)
        def envuelta(*args, **kwargs):
            if 'perfil' not in session:
                return redirect(url_for('login'))
            credenciales()  # corta la sesión si pasó el tiempo de inactividad
            if not puede(vista, modificar):
                abort(403)
            return f(*args, **kwargs)
        return envuelta
    return deco


@app.context_processor
def menu():
    if 'perfil' not in session:
        return {}
    visibles = [(s, [e for e in entradas if puede(e[0])]) for s, entradas in MENU]
    return {'MENU': [(s, e) for s, e in visibles if e], 'puede': puede, 'activo': ACTIVO.get(request.endpoint, request.endpoint),
            'es_pes': session['perfil'] == 'PES'}


def otra_sede():
    return 'EMPAQUE' if session['sede'] == 'PLANTA' else 'PLANTA'


def siguiente_codigo(prefijo):
    # El prefijo es fijo en el código (MET-año- o TAP-ENV-), nunca viene del formulario.
    tabla = 'lote_produccion' if prefijo.startswith('MET') else 'envio'
    fila = q1(SQL('SELECT coalesce(max(substr(codigo, {}::int)::int), 0) + 1 AS n FROM {} WHERE codigo LIKE {}').format(
        len(prefijo) + 1, Identifier(tabla), Literal(prefijo + '%')))
    return f"{prefijo}{fila['n']:04d}"


def zonas_prioridad(dias, corte):
    filas = q("""
        WITH t AS (SELECT zona_clave, count(DISTINCT trampa) AS trampas, sum(silvestres) AS silvestres, count(*) AS revisiones
                   FROM trampeo.revision WHERE fecha > %(c)s::date - %(d)s AND fecha <= %(c)s GROUP BY zona_clave),
             l AS (SELECT zona_id, sum(cantidad) AS liberadas FROM liberacion
                   WHERE fecha::date > %(c)s::date - %(d)s AND fecha::date <= %(c)s GROUP BY zona_id)
        SELECT z.*, coalesce(t.trampas, 0) AS trampas, coalesce(t.silvestres, 0) AS silvestres,
               coalesce(t.silvestres::numeric / nullif(t.revisiones, 0), 0) AS mtd, coalesce(l.liberadas, 0) AS liberadas
        FROM zona_liberacion z LEFT JOIN t ON t.zona_clave = z.clave LEFT JOIN l ON l.zona_id = z.id""",
              {'c': corte, 'd': dias})
    for z in filas:
        z['prioridad'] = prioridad_zona(z['silvestres'], z['liberadas'])
        z['sugeridas'] = sugeridas_semana(z['superficie_ha'], z['densidad_objetivo'], z['prioridad'])
    orden = {'ALTA': 0, 'MEDIA': 1, 'NORMAL': 2}
    return sorted(filas, key=lambda z: (orden[z['prioridad']], -z['mtd'], z['clave']))


def paginar():
    return max(request.args.get('pagina', 1, type=int), 1)


# ---------- Acceso ----------

@app.route('/', methods=['GET', 'POST'])
def login():
    sede = request.values.get('sede', 'PLANTA')
    if sede not in SERVIDORES:
        sede = 'PLANTA'
    error = None
    if request.method == 'POST':
        usuario, clave = request.form.get('usuario', '').strip(), request.form.get('clave', '')
        try:
            with conectar(sede, usuario, clave) as con:
                yo = con.execute('SELECT * FROM responsable WHERE usuario_bd = current_user').fetchone()
                quitados = {p['vista']: (p['ver'], p['modificar']) for p in con.execute(
                    'SELECT * FROM permiso_vista WHERE responsable_id = %s', (yo['id'] if yo else None,))}
        except psycopg.OperationalError as e:
            error = error_conexion(e, 'la planta' if sede == 'PLANTA' else 'el centro de empaque')
        else:
            if not yo:
                error = 'Tu usuario no tiene perfil en el sistema. Pídeselo al administrador.'
            elif not yo['activo']:
                error = 'Tu usuario está dado de baja.'
            elif yo['sede'] != sede:
                error = 'Tu perfil es de la otra sede: elígela arriba.'
            else:
                perfil = SEDE_PERFIL[sede]
                abrir_sesion(sede, usuario, clave, perfil=perfil, responsable_id=yo['id'],
                             nombre=f"{yo['nombre']} {yo['ap_paterno']} {yo['ap_materno'] or ''}".strip(),
                             nombre_corto=yo['nombre'].split()[0], empleado=yo['num_empleado'],
                             permisos=permisos(perfil, quitados), entrada=datetime.now().strftime('%d/%m/%Y %H:%M'))
                return redirect(url_for('inicio'))
    return render_template('login.html', sede=sede, error=error, servidores=SERVIDORES)


@app.post('/salir')
def salir():
    cerrar_sesion()
    return redirect(url_for('login'))


@app.route('/inicio')
@requiere('inicio')
def inicio():
    if session['perfil'] == 'PES':
        k = q1("""SELECT count(*) FILTER (WHERE fecha_produccion >= date_trunc('week', current_date)) AS semana,
                         coalesce(sum(cantidad_pupas) FILTER (WHERE fecha_produccion >= date_trunc('week', current_date)), 0) AS pupas,
                         count(*) FILTER (WHERE estado = 'PRODUCIDO') AS sin_cc,
                         count(*) FILTER (WHERE estado = 'RECHAZADO' AND fecha_produccion >= date_trunc('month', current_date)) AS rechazados
                  FROM v_lote""")
        rechazo = q1("""SELECT l.codigo, c.vuelo < 65 AS vuelo, c.emergencia < 75 AS emergencia FROM v_lote l
                        JOIN control_calidad c ON c.lote_id = l.id AND NOT c.aprobado
                        WHERE l.estado = 'RECHAZADO' AND l.fecha_produccion >= date_trunc('month', current_date) ORDER BY l.fecha_produccion DESC LIMIT 1""")
        lotes = q('SELECT codigo, fecha_produccion, cantidad_pupas, estado FROM v_lote ORDER BY fecha_produccion DESC, codigo DESC LIMIT 6')
        pendientes = q("SELECT codigo, fecha_irradiacion FROM v_lote WHERE estado = 'PRODUCIDO' ORDER BY fecha_produccion DESC LIMIT 3")
        return render_template('inicio_pes.html', k=k, rechazo=rechazo, lotes=lotes, pendientes=pendientes)
    k = q1("""SELECT (SELECT count(*) FROM v_lote WHERE estado = 'APROBADO') AS por_despachar,
                     (SELECT count(*) FROM v_envio WHERE estado = 'EN CAMINO') AS en_camino,
                     (SELECT coalesce(sum(cantidad), 0) FROM liberacion WHERE fecha >= date_trunc('month', current_date)) AS liberadas_mes""")
    envios = q("""SELECT e.codigo, l.codigo AS lote, v.tipo, v.matricula, e.cantidad FROM v_envio e
                  JOIN lote_produccion l ON l.id = e.lote_id JOIN vehiculo v ON v.id = e.vehiculo_id
                  WHERE e.estado = 'EN CAMINO' ORDER BY e.fecha_salida DESC LIMIT 5""")
    altas = [z for z in zonas_prioridad(14, date.today()) if z['prioridad'] == 'ALTA']
    return render_template('inicio_pem.html', k=k, envios=envios, altas=altas)


# ---------- Lotes ----------

@app.route('/lotes')
@requiere('lotes')
def lotes():
    f = {k: request.args.get(k, '') for k in ('q', 'estado', 'desde', 'hasta')}
    filtros, p = ['true'], dict(f)
    if f['q']:
        filtros.append('l.codigo ILIKE %(patron)s')
        p['patron'] = f"%{f['q']}%"
    if f['estado']:
        filtros.append('l.estado = %(estado)s')
    if f['desde']:
        filtros.append('l.fecha_produccion >= %(desde)s')
    if f['hasta']:
        filtros.append('l.fecha_produccion <= %(hasta)s')
    donde = SQL(' AND '.join(filtros))
    pagina = paginar()
    total = q1(SQL('SELECT count(*) AS n FROM v_lote l WHERE {}').format(donde), p)['n']
    filas = q(SQL("""SELECT l.*, c.clave AS destino FROM v_lote l JOIN centro_empaque c ON c.id = l.centro_empaque_id
                     WHERE {} ORDER BY l.fecha_produccion DESC, l.codigo DESC LIMIT %(lim)s OFFSET %(off)s""").format(donde),
              {**p, 'lim': POR_PAGINA, 'off': (pagina - 1) * POR_PAGINA})
    return render_template('lista_lotes.html', f=f, filas=filas, total=total, pagina=pagina, por=POR_PAGINA)


@app.route('/lotes/nuevo', methods=['GET', 'POST'])
@requiere('nuevo_lote', True)
def nuevo_lote():
    return formulario_lote(None)


@app.route('/lotes/<codigo>/modificar', methods=['GET', 'POST'])
@requiere('lotes', True)
def modificar_lote(codigo):
    return formulario_lote(codigo)


def formulario_lote(codigo):
    lote = None
    if codigo:
        lote = q1('SELECT * FROM v_lote WHERE codigo = %s', (codigo,)) or abort(404)
        if lote['estado'] not in ('PRODUCIDO', 'APROBADO'):
            flash('Solo se modifican lotes que aún no salen de la planta.')
            return redirect(url_for('estado_lote', codigo=codigo))
    errores = []
    if request.method == 'POST':
        f = request.form
        try:
            d = {'fp': date.fromisoformat(f['fecha_produccion']), 'fi': date.fromisoformat(f['fecha_irradiacion']),
                 'dosis': Decimal(f['dosis_gy']), 'pupas': entero(f['cantidad_pupas']), 'cepa': f['cepa'],
                 'tinte': f['tinte'], 'centro': int(f['centro_empaque_id']), 'obs': f.get('observaciones', '').strip() or None}
        except (KeyError, ValueError, InvalidOperation):
            errores.append('Revisa que las fechas y los números estén completos.')
        else:
            if d['fi'] < d['fp']:
                errores.append('La irradiación debe ser el mismo día o después de la producción.')
            if not 90 <= d['dosis'] <= 160:
                errores.append('La dosis debe estar entre 90 y 160 Gy.')
            if d['pupas'] <= 0:
                errores.append('La cantidad de pupas debe ser mayor que cero.')
        if not errores:
            try:
                with db().transaction():
                    if lote:
                        db().execute("""UPDATE lote_produccion SET fecha_produccion = %(fp)s, fecha_irradiacion = %(fi)s,
                                        dosis_gy = %(dosis)s, cantidad_pupas = %(pupas)s, cepa = %(cepa)s, tinte = %(tinte)s,
                                        centro_empaque_id = %(centro)s, observaciones = %(obs)s WHERE id = %(id)s""",
                                     {**d, 'id': lote['id']})
                        auditar(db(), f'Modificó el lote {codigo}')
                    else:
                        codigo = siguiente_codigo(f"MET-{d['fp'].year}-")
                        db().execute("""INSERT INTO lote_produccion (codigo, fecha_produccion, fecha_irradiacion, dosis_gy,
                                        cantidad_pupas, cepa, tinte, centro_empaque_id, responsable_id, observaciones)
                                        VALUES (%(codigo)s, %(fp)s, %(fi)s, %(dosis)s, %(pupas)s, %(cepa)s, %(tinte)s,
                                        %(centro)s, %(resp)s, %(obs)s)""",
                                     {**d, 'codigo': codigo, 'resp': session['responsable_id']})
                        auditar(db(), f'Registró el lote {codigo}')
            except psycopg.Error as e:
                errores.append(mensaje_bd(e))
            else:
                flash(f'Lote {codigo} guardado.')
                return redirect(url_for('estado_lote', codigo=codigo))
    hoy = date.today()
    v = lote or {'fecha_produccion': hoy, 'fecha_irradiacion': hoy, 'dosis_gy': '120.0', 'cantidad_pupas': '',
                 'cepa': '', 'tinte': '', 'centro_empaque_id': '', 'observaciones': ''}
    if request.method == 'POST':
        v = request.form
    return render_template('nuevo_lote.html', lote=lote, v=v, errores=errores,
                           codigo=lote['codigo'] if lote else siguiente_codigo(f'MET-{hoy.year}-'),
                           centros=q('SELECT * FROM centro_empaque ORDER BY clave'))


@app.route('/lotes/estado')
@requiere('estado_lote')
def estado_lote_ultimo():
    fila = q1("SELECT codigo FROM v_lote ORDER BY (estado = 'PRODUCIDO') DESC, fecha_produccion DESC LIMIT 1")
    if not fila:
        flash('Todavía no hay lotes registrados.')
        return redirect(url_for('inicio'))
    return redirect(url_for('estado_lote', codigo=fila['codigo']))


@app.route('/lotes/<codigo>', methods=['GET', 'POST'])
@requiere('estado_lote')
def estado_lote(codigo):
    lote = q1("""SELECT l.*, c.clave AS centro_clave, c.nombre AS centro_nombre, r.num_empleado, r.nombre AS r_nombre, r.ap_paterno
                 FROM v_lote l JOIN centro_empaque c ON c.id = l.centro_empaque_id JOIN responsable r ON r.id = l.responsable_id
                 WHERE l.codigo = %s""", (codigo,)) or abort(404)
    etapa = 'PLANTA' if session['perfil'] == 'PES' else 'EMPAQUE'
    errores = []
    if request.method == 'POST':
        if not puede('estado_lote', True):
            abort(403)
        try:
            em, vu, es, pe = (Decimal(request.form[k]) for k in ('emergencia', 'vuelo', 'esterilidad', 'peso_mg'))
        except (KeyError, InvalidOperation):
            errores.append('Llena las cuatro mediciones con números.')
        else:
            if any(not 0 <= x <= 100 for x in (em, vu, es)):
                errores.append('Los porcentajes van de 0 a 100.')
            if pe <= 0:
                errores.append('El peso debe ser mayor que cero.')
        if not errores:
            try:
                with db().transaction():
                    db().execute("""INSERT INTO control_calidad (lote_id, etapa, emergencia, vuelo, esterilidad, peso_mg, responsable_id, observaciones)
                                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                                    ON CONFLICT (lote_id, etapa) DO UPDATE SET emergencia = EXCLUDED.emergencia, vuelo = EXCLUDED.vuelo,
                                      esterilidad = EXCLUDED.esterilidad, peso_mg = EXCLUDED.peso_mg, fecha = current_date,
                                      responsable_id = EXCLUDED.responsable_id, observaciones = EXCLUDED.observaciones""",
                                 (lote['id'], etapa, em, vu, es, pe, session['responsable_id'],
                                  request.form.get('observaciones', '').strip() or None))
                    auditar(db(), f'Registró calidad {etapa.lower()} de {codigo}')
            except psycopg.Error as e:
                errores.append(mensaje_bd(e))
            else:
                flash('Control de calidad guardado.')
                return redirect(url_for('estado_lote', codigo=codigo))
    calidades = {c['etapa']: c for c in q("""SELECT c.*, r.num_empleado FROM control_calidad c
                                              JOIN responsable r ON r.id = c.responsable_id WHERE c.lote_id = %s""", (lote['id'],))}
    envios = q("""SELECT e.codigo, e.cantidad, e.liberadas, e.estado, v.tipo, v.matricula,
                         (SELECT string_agg(DISTINCT z.clave || ' · ' || z.municipio, ', ') FROM liberacion li
                          JOIN zona_liberacion z ON z.id = li.zona_id WHERE li.envio_id = e.id) AS zonas
                  FROM v_envio e JOIN vehiculo v ON v.id = e.vehiculo_id WHERE e.lote_id = %s ORDER BY e.fecha_salida""", (lote['id'],))
    v = request.form if request.method == 'POST' else calidades.get(etapa, {})
    return render_template('estado_lote.html', lote=lote, calidades=calidades, envios=envios, etapa=etapa, v=v, errores=errores)


# ---------- Envíos ----------

@app.route('/envios')
@requiere('envios')
def envios():
    f = {k: request.args.get(k, '') for k in ('q', 'estado', 'tipo')}
    filtros, p = ['true'], dict(f)
    if f['q']:
        filtros.append('(e.codigo ILIKE %(patron)s OR l.codigo ILIKE %(patron)s)')
        p['patron'] = f"%{f['q']}%"
    if f['estado']:
        filtros.append('e.estado = %(estado)s')
    if f['tipo']:
        filtros.append('v.tipo = %(tipo)s')
    donde = SQL(' AND '.join(filtros))
    desde = SQL('FROM v_envio e JOIN lote_produccion l ON l.id = e.lote_id JOIN vehiculo v ON v.id = e.vehiculo_id WHERE {}').format(donde)
    pagina = paginar()
    total = q1(SQL('SELECT count(*) AS n {}').format(desde), p)['n']
    filas = q(SQL('SELECT e.*, l.codigo AS lote, v.tipo, v.matricula {} ORDER BY e.fecha_salida DESC LIMIT %(lim)s OFFSET %(off)s').format(desde),
              {**p, 'lim': POR_PAGINA, 'off': (pagina - 1) * POR_PAGINA})
    resumen = Counter({r['estado']: r['n'] for r in q('SELECT estado, count(*) AS n FROM v_envio GROUP BY estado')})
    return render_template('lista_envios.html', f=f, filas=filas, total=total, pagina=pagina, por=POR_PAGINA, resumen=resumen)


@app.route('/envios/nuevo', methods=['GET', 'POST'])
@requiere('nuevo_envio', True)
def nuevo_envio():
    return formulario_envio(None)


@app.route('/envios/<codigo>/modificar', methods=['GET', 'POST'])
@requiere('envios', True)
def modificar_envio(codigo):
    return formulario_envio(codigo)


def formulario_envio(codigo):
    envio = None
    if codigo:
        envio = q1('SELECT * FROM v_envio WHERE codigo = %s', (codigo,)) or abort(404)
        if envio['estado'] != 'EN CAMINO':
            flash('Solo se modifican envíos en camino.')
            return redirect(url_for('estado_envio', codigo=codigo))
    lotes = q("""SELECT l.id, l.codigo, l.cantidad_pupas, l.enviadas, cp.fecha AS fecha_cc FROM v_lote l
                 JOIN control_calidad cp ON cp.lote_id = l.id AND cp.etapa = 'PLANTA'
                 WHERE l.calidad_planta AND l.estado <> 'RECHAZADO' AND (l.cantidad_pupas > l.enviadas OR l.id = %s)
                 ORDER BY l.fecha_produccion""", (envio['lote_id'] if envio else None,))
    for l in lotes:  # lo que lleva este mismo envío vuelve a estar disponible al modificarlo
        l['disponibles'] = l['cantidad_pupas'] - l['enviadas'] + (envio['cantidad'] if envio and envio['lote_id'] == l['id'] else 0)
    vehiculos = q('SELECT * FROM vehiculo ORDER BY activo DESC, tipo, matricula')
    errores = []
    if request.method == 'POST':
        f = request.form
        try:
            d = {'lote': int(f['lote_id']), 'veh': int(f['vehiculo_id']), 'cant': entero(f['cantidad']),
                 'salida': datetime.fromisoformat(f['fecha_salida']), 'temp': Decimal(f['temperatura'])}
        except (KeyError, ValueError, InvalidOperation):
            errores.append('Revisa que todos los campos estén llenos.')
        else:
            lote = next((l for l in lotes if l['id'] == d['lote']), None)
            veh = next((x for x in vehiculos if x['id'] == d['veh']), None)
            if not lote:
                errores.append('Elige un lote aprobado y con pupas disponibles.')
            if not veh or not veh['activo']:
                errores.append('Elige un vehículo en servicio.')
            if lote and veh:
                errores += errores_envio(d['cant'], veh['capacidad'], lote['disponibles'])
        if not errores:
            try:
                with db().transaction():
                    if envio:
                        db().execute("""UPDATE envio SET lote_id = %(lote)s, vehiculo_id = %(veh)s, cantidad = %(cant)s,
                                        fecha_salida = %(salida)s, temperatura = %(temp)s WHERE id = %(id)s""", {**d, 'id': envio['id']})
                        auditar(db(), f'Modificó el envío {codigo}')
                    else:
                        codigo = siguiente_codigo('TAP-ENV-')
                        db().execute("""INSERT INTO envio (codigo, lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id)
                                        VALUES (%(codigo)s, %(lote)s, %(veh)s, %(salida)s, %(cant)s, %(temp)s, %(resp)s)""",
                                     {**d, 'codigo': codigo, 'resp': session['responsable_id']})
                        auditar(db(), f'Registró el envío {codigo}')
            except psycopg.Error as e:
                errores.append(mensaje_bd(e))
            else:
                flash(f'Envío {codigo} guardado.')
                return redirect(url_for('estado_envio', codigo=codigo))
    if request.method == 'POST':
        v = request.form
    elif envio:
        v = {**envio, 'fecha_salida': envio['fecha_salida'].strftime('%Y-%m-%dT%H:%M')}
    else:
        v = {'lote_id': request.args.get('lote', ''), 'vehiculo_id': '', 'cantidad': '', 'temperatura': '3.0',
             'fecha_salida': datetime.now().strftime('%Y-%m-%dT%H:%M')}
    return render_template('nuevo_envio.html', envio=envio, v=v, lotes=lotes, vehiculos=vehiculos, errores=errores)


@app.route('/envios/estado')
@requiere('estado_envio')
def estado_envio_ultimo():
    fila = q1("SELECT codigo FROM v_envio ORDER BY (estado = 'EN CAMINO') DESC, fecha_salida DESC LIMIT 1")
    if not fila:
        flash('Todavía no hay envíos registrados.')
        return redirect(url_for('inicio'))
    return redirect(url_for('estado_envio', codigo=fila['codigo']))


@app.route('/envios/<codigo>', methods=['GET', 'POST'])
@requiere('estado_envio')
def estado_envio(codigo):
    envio = q1("""SELECT e.*, l.codigo AS lote, v.tipo, v.matricula, v.modelo, r.num_empleado, r.nombre AS r_nombre, r.ap_paterno
                  FROM v_envio e JOIN lote_produccion l ON l.id = e.lote_id JOIN vehiculo v ON v.id = e.vehiculo_id
                  JOIN responsable r ON r.id = e.responsable_id WHERE e.codigo = %s""", (codigo,)) or abort(404)
    restantes = envio['cantidad'] - envio['liberadas']
    todas = zonas_prioridad(14, date.today())
    zona_sel = request.values.get('zona_clave') or request.args.get('zona') or (todas[0]['clave'] if todas else '')
    errores = []
    if request.method == 'POST':
        if not puede('estado_envio', True):
            abort(403)
        if envio['estado'] != 'EN CAMINO':
            errores.append('Este envío ya no está en camino.')
        elif request.form.get('accion') == 'cancelar':
            db().execute('UPDATE envio SET cancelado = true WHERE id = %s', (envio['id'],))
            auditar(db(), f'Canceló el envío {codigo}')
            flash(f'Envío {codigo} cancelado.')
            return redirect(url_for('estado_envio', codigo=codigo))
        else:
            f = request.form
            try:
                d = {'fecha': datetime.fromisoformat(f['fecha']), 'cant': entero(f['cantidad']),
                     'sup': Decimal(f['superficie_ha'].replace(',', '')), 'piloto': int(f['piloto_id']),
                     'alt': entero(f['altitud_m']) if f.get('altitud_m', '').strip() else None}
            except (KeyError, ValueError, InvalidOperation):
                errores.append('Revisa que todos los campos estén llenos.')
            else:
                zona = next((z for z in todas if z['clave'] == zona_sel), None)
                if not zona:
                    errores.append('Elige una zona.')
                if not 0 < d['cant'] <= restantes:
                    errores.append(f'Las moscas liberadas van de 1 a {restantes:,} (lo que lleva el envío).')
                if envio['tipo'] == 'AVIONETA' and not d['alt']:
                    errores.append('La altitud es obligatoria porque el envío va en avioneta.')
            if not errores:
                try:
                    with db().transaction():
                        db().execute("""INSERT INTO liberacion (envio_id, zona_id, fecha, cantidad, superficie_ha, altitud_m, piloto_id)
                                        VALUES (%s, %s, %s, %s, %s, %s, %s)""",
                                     (envio['id'], zona['id'], d['fecha'], d['cant'], d['sup'], d['alt'], d['piloto']))
                        auditar(db(), f'Registró liberación de {codigo}')
                except psycopg.Error as e:
                    errores.append(mensaje_bd(e))
                else:
                    flash('Liberación registrada.')
                    return redirect(url_for('estado_envio', codigo=codigo))
    liberaciones = q("""SELECT li.*, z.clave, z.nombre FROM liberacion li JOIN zona_liberacion z ON z.id = li.zona_id
                        WHERE li.envio_id = %s ORDER BY li.fecha""", (envio['id'],))
    pilotos = q("SELECT id, num_empleado, puesto FROM responsable WHERE sede = 'EMPAQUE' AND activo ORDER BY num_empleado")
    v = request.form if request.method == 'POST' else {
        'fecha': datetime.now().strftime('%Y-%m-%dT%H:%M'), 'cantidad': restantes, 'superficie_ha': '', 'altitud_m': '', 'piloto_id': ''}
    return render_template('estado_envio.html', envio=envio, restantes=restantes, liberaciones=liberaciones, pilotos=pilotos,
                           zonas=todas, sugeridas=todas[:5], zona_sel=zona_sel, v=v, errores=errores)


# ---------- Reportes y ajustes ----------

@app.route('/zonas')
@requiere('zonas')
def zonas():
    dias = 30 if request.args.get('dias') == '30' else 14
    try:
        corte = date.fromisoformat(request.args.get('corte', ''))
    except ValueError:
        corte = date.today()
    filas = zonas_prioridad(dias, corte)
    if request.args.get('formato') == 'csv':
        salida = io.StringIO()
        w = csv.writer(salida)
        w.writerow(['zona', 'municipio', 'trampas', 'silvestres', 'mtd', 'liberadas', 'prioridad', 'sugeridas_semana'])
        for z in filas:
            w.writerow([z['clave'], z['municipio'], z['trampas'], z['silvestres'], f"{z['mtd']:.4f}", z['liberadas'], z['prioridad'], z['sugeridas']])
        return Response('﻿' + salida.getvalue(), mimetype='text/csv',
                        headers={'Content-Disposition': f'attachment; filename=zonas_{corte}_{dias}d.csv'})
    barras = q("""SELECT z.clave, z.municipio, sum(li.cantidad) AS total FROM liberacion li JOIN zona_liberacion z ON z.id = li.zona_id
                  WHERE li.fecha::date <= %s GROUP BY z.id ORDER BY total DESC LIMIT 6""", (corte,))
    return render_template('zonas.html', filas=filas, dias=dias, corte=corte, barras=barras,
                           conteo=Counter(z['prioridad'] for z in filas))


@app.route('/perfil', methods=['GET', 'POST'])
@requiere('perfil')
def perfil():
    errores = []
    if request.method == 'POST':
        actual, nueva, conf = (request.form.get(k, '') for k in ('actual', 'nueva', 'confirmar'))
        if actual != credenciales()[2]:
            errores.append('La contraseña actual no coincide.')
        elif nueva != conf:
            errores.append('La nueva contraseña y su confirmación no coinciden.')
        elif not contrasena_valida(nueva):
            errores.append('Mínimo 10 caracteres, con número y símbolo.')
        else:
            orden = SQL('ALTER ROLE CURRENT_USER PASSWORD {}').format(Literal(nueva))
            try:
                with db_en(otra_sede()) as otra:  # primero la otra sede: si no responde, no cambia nada
                    otra.execute(orden)
                db().execute(orden)
                auditar(db(), 'Cambió su contraseña')
            except psycopg.Error as e:
                errores.append('No se pudo cambiar en los dos servidores: ' + mensaje_bd(e))
            else:
                cambiar_clave_guardada(nueva)
                flash('Contraseña cambiada en los dos servidores.')
                return redirect(url_for('perfil'))
    yo = q1('SELECT * FROM responsable WHERE id = %s', (session['responsable_id'],))
    return render_template('perfil.html', yo=yo, vistas=VISTAS, errores=errores, servidor=SERVIDORES[session['sede']])


if __name__ == '__main__':
    app.run(port=5000, debug=False)
