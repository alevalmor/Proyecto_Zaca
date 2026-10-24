import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from reglas import (calidad_aprobada, contrasena_valida, entero, errores_envio, permisos, prioridad_zona,
                    sugeridas_semana, usuario_bd_valido)


def test_permisos_solo_restan():
    p = permisos('PEM', {'envios': (True, False), 'nuevo_lote': (True, True)})
    assert p['envios'] == (True, False)
    assert p['nuevo_lote'] == (False, False)  # el admin no puede dar más de lo que permite el perfil
    assert permisos('PES', {'lotes': (False, True)})['lotes'] == (False, False)  # sin ver no se modifica


def test_calidad():
    assert calidad_aprobada(75, 65, 99.5)
    assert not calidad_aprobada(86.4, 64.9, 99.9)


def test_zonas():
    assert prioridad_zona(1, 5_000_000) == 'ALTA'
    assert prioridad_zona(0, 0) == 'MEDIA'
    assert prioridad_zona(0, 10) == 'NORMAL'
    assert sugeridas_semana(100, 3000, 'ALTA') == 600_000


def test_envio():
    assert errores_envio(24_800_000, 32_000_000, 49_107_000) == []
    assert len(errores_envio(40_000_000, 32_000_000, 30_000_000)) == 2
    assert errores_envio(0, 1, 1) == ['La cantidad debe ser mayor que cero']


def test_entradas():
    assert entero('52,000,000') == 52_000_000
    assert contrasena_valida('Moscamed#7Q2k') and not contrasena_valida('corta1#') and not contrasena_valida('sinnumeros!!')
    assert usuario_bd_valido('i.coutino') and not usuario_bd_valido('x"; DROP ROLE') and not usuario_bd_valido('Mayus')


if __name__ == '__main__':
    for nombre, f in list(globals().items()):
        if nombre.startswith('test_'):
            f()
    print('ok')
