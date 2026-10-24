"""Reglas de negocio sin base de datos (se prueban en tests/test_reglas.py)."""
import re

VISTAS = [
    ('nuevo_lote', 'Nuevo lote'),
    ('lotes', 'Lista de lotes'),
    ('estado_lote', 'Estado de lote / calidad'),
    ('nuevo_envio', 'Nuevo envío'),
    ('envios', 'Lista de envíos'),
    ('estado_envio', 'Estado de envío / liberación'),
    ('zonas', 'Zonas prioritarias'),
]

# Lo máximo que permite cada perfil: (ver, modificar). La base de datos lo hace cumplir con sus roles.
PERMISOS_PERFIL = {
    'PES': {'nuevo_lote': (True, True), 'lotes': (True, True), 'estado_lote': (True, True),
            'nuevo_envio': (False, False), 'envios': (True, False), 'estado_envio': (True, False),
            'zonas': (True, False)},
    'PEM': {'nuevo_lote': (False, False), 'lotes': (True, False), 'estado_lote': (True, True),
            'nuevo_envio': (True, True), 'envios': (True, True), 'estado_envio': (True, True),
            'zonas': (True, False)},
}

SEDE_PERFIL = {'PLANTA': 'PES', 'EMPAQUE': 'PEM'}
ROL_PERFIL = {'PES': 'rol_produccion', 'PEM': 'rol_empaque'}


def permisos(perfil, quitados):
    """quitados: {vista: (ver, modificar)} guardado por el administrador; solo puede restar."""
    res = {}
    for vista, (ver, mod) in PERMISOS_PERFIL[perfil].items():
        qv, qm = quitados.get(vista, (True, True))
        ver = ver and qv
        res[vista] = (ver, mod and qm and ver)
    return res


def calidad_aprobada(emergencia, vuelo, esterilidad):
    # Misma fórmula que la columna generada control_calidad.aprobado.
    return emergencia >= 75 and vuelo >= 65 and esterilidad >= 99.5


def prioridad_zona(silvestres, liberadas):
    if silvestres > 0:
        return 'ALTA'
    return 'MEDIA' if liberadas == 0 else 'NORMAL'


def sugeridas_semana(superficie_ha, densidad, prioridad):
    return int(superficie_ha * densidad * (2 if prioridad == 'ALTA' else 1))


def errores_envio(cantidad, capacidad, disponibles):
    errores = []
    if cantidad <= 0:
        errores.append('La cantidad debe ser mayor que cero')
    if cantidad > capacidad:
        errores.append('No cabe en el vehículo')
    if cantidad > disponibles:
        errores.append('Supera lo que queda del lote')
    return errores


def contrasena_valida(clave):
    return len(clave) >= 10 and bool(re.search(r'\d', clave)) and bool(re.search(r'[^\w\s]', clave))


def usuario_bd_valido(usuario):
    # Se interpola como identificador en CREATE ROLE; solo letras, números, punto y guion bajo.
    return re.fullmatch(r'[a-z][a-z0-9_.]{2,30}', usuario) is not None


def entero(texto):
    """'52,000,000' -> 52000000; acepta lo que escribe la gente en los formularios."""
    return int(str(texto).replace(',', '').replace(' ', '').strip() or 0)


def miles(n):
    return f'{int(n or 0):,}'
