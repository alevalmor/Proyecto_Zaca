"""Conexión a PostgreSQL con el usuario de la persona: los permisos los aplica la base de datos con sus roles."""
import os
import secrets
import time

import psycopg
from psycopg.rows import dict_row
from flask import abort, flash, g, redirect, request, session, url_for

SERVIDORES = {
    'PLANTA': (os.environ.get('PG_HOST_PLANTA', '192.168.10.10'), int(os.environ.get('PG_PORT_PLANTA', 9999))),
    'EMPAQUE': (os.environ.get('PG_HOST_EMPAQUE', '192.168.20.10'), int(os.environ.get('PG_PORT_EMPAQUE', 9999))),
}
BASE = os.environ.get('PG_DB', 'moscamed')
INACTIVIDAD = 5 * 60  # segundos; el diseño pide cierre a los 5 min sin uso

# shortcut: contraseñas en memoria de este proceso (nunca en la cookie); con varios workers o reinicios
# se pierden las sesiones: usar un solo proceso o mover esto a un almacén compartido (Redis).
_credenciales = {}


def conectar(sede, usuario, clave):
    host, puerto = SERVIDORES[sede]
    return psycopg.connect(host=host, port=puerto, dbname=BASE, user=usuario, password=clave,
                           row_factory=dict_row, autocommit=True, connect_timeout=5)


def abrir_sesion(sede, usuario, clave, **datos):
    token = secrets.token_urlsafe(32)
    _credenciales[token] = [sede, usuario, clave]
    session.clear()
    session.update(token=token, visto=time.time(), sede=sede, usuario=usuario, **datos)


def cerrar_sesion():
    _credenciales.pop(session.get('token'), None)
    session.clear()


def credenciales():
    cred = _credenciales.get(session.get('token'))
    if not cred or time.time() - session.get('visto', 0) > INACTIVIDAD:
        cerrar_sesion()
        abort(401)
    session['visto'] = time.time()
    return cred


def cambiar_clave_guardada(clave):
    credenciales()[2] = clave


def db():
    if 'db' not in g:
        g.db = conectar(*credenciales())
    return g.db


def db_en(sede):
    """Conexión al servidor de otra sede con el mismo usuario (los roles no se replican)."""
    _, usuario, clave = credenciales()
    return conectar(sede, usuario, clave)


def cerrar_db(_exc=None):
    con = g.pop('db', None)
    if con is not None:
        con.close()


def q(sql, params=None):
    return db().execute(sql, params).fetchall()


def q1(sql, params=None):
    return db().execute(sql, params).fetchone()


def auditar(con, accion):
    con.execute('INSERT INTO auditoria (accion) VALUES (%s)', (accion,))


def error_conexion(error, servidor):
    texto = str(error)
    if 'password' in texto:
        return 'Usuario o contraseña incorrectos.'
    if 'not permitted to log in' in texto:
        return 'Tu usuario está dado de baja.'
    return f'No responde el servidor de {servidor}.'


def mensaje_bd(error):
    if isinstance(error, psycopg.errors.InsufficientPrivilege):
        return 'La base de datos no permite esta acción con tu perfil.'
    if isinstance(error, psycopg.errors.UniqueViolation):
        return 'Ya existe un registro con ese código o usuario; vuelve a intentarlo.'
    diag = getattr(error, 'diag', None)
    return 'La base de datos rechazó los datos: ' + (diag.message_primary if diag and diag.message_primary else str(error))


def preparar(app, login, inicio):
    """Lo común de los dos programas: sesión, CSRF, errores y filtros de plantilla."""
    app.secret_key = os.environ.get('SECRET_KEY') or secrets.token_hex(32)
    app.config.update(SESSION_COOKIE_SAMESITE='Lax', SESSION_COOKIE_HTTPONLY=True)
    app.teardown_appcontext(cerrar_db)

    @app.before_request
    def revisar_csrf():
        if request.method == 'POST' and request.form.get('csrf') != session.get('csrf'):
            abort(400)

    @app.context_processor
    def comunes():
        session.setdefault('csrf', secrets.token_urlsafe(16))

        def pagina_url(n):
            args = request.args.to_dict()
            args['pagina'] = n
            return url_for(request.endpoint, **(request.view_args or {}), **args)
        return {'csrf': session['csrf'], 'pagina_url': pagina_url}

    @app.errorhandler(401)
    def sin_sesion(_e):
        flash('Tu sesión terminó. Vuelve a iniciar sesión.')
        return redirect(url_for(login))

    @app.errorhandler(403)
    @app.errorhandler(psycopg.errors.InsufficientPrivilege)
    def sin_permiso(_e):
        flash('Tu perfil no tiene permiso para esa acción.')
        return redirect(url_for(inicio))

    app.add_template_filter(lambda n: f'{int(n or 0):,}', 'miles')
    app.add_template_filter(lambda n: f'{float(n or 0) / 1e6:,.1f} M', 'millones')
    app.add_template_filter(lambda d: d.strftime('%d/%m/%Y') if d else '—', 'fecha')
    app.add_template_filter(lambda d: d.strftime('%d/%m/%Y %H:%M') if d else '—', 'fechahora')
