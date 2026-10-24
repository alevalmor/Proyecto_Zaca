-- Datos de ejemplo para desarrollo. Correr en UN solo servidor: la replicación los copia al otro.
-- Fechas relativas a hoy para que el inicio y los reportes muestren algo.
SET client_encoding = 'UTF8';  -- psql en Windows lee el archivo como WIN1252 y rompe los acentos

INSERT INTO responsable (num_empleado, nombre, ap_paterno, ap_materno, puesto, telefono, correo, sede, perfil, usuario_bd, fecha_alta) VALUES
 ('MET-0003', 'Diana', 'Rodríguez', 'Roblero', 'Técnico de irradiación', '9628472671', 'diana.rodriguez@moscamed.ejemplo.mx', 'PLANTA', 'PES', 'usr_planta', '2017-12-22'),
 ('MET-0001', 'Pedro', 'Mendoza', 'Ruiz', 'Analista de control de calidad', '9621110001', 'pedro.mendoza@moscamed.ejemplo.mx', 'PLANTA', 'PES', 'p.mendoza', '2015-03-02'),
 ('TAP-0008', 'Luis', 'Pineda', 'Matías', 'Jefe de empaque', '9621110008', 'luis.pineda@moscamed.ejemplo.mx', 'EMPAQUE', 'PEM', 'usr_empaque', '2016-06-15'),
 ('TAP-0021', 'Karla', 'Velasco', 'Toledo', 'Encargada de dispersión', '9621110021', 'karla.velasco@moscamed.ejemplo.mx', 'EMPAQUE', 'PED', 'usr_dispersion', '2019-01-10'),
 ('TAP-0030', 'Óscar', 'Cifuentes', 'Roblero', 'Encargado de aeropuerto', '9621110030', 'oscar.cifuentes@moscamed.ejemplo.mx', 'EMPAQUE', 'PEA', 'usr_aeropuerto', '2020-08-01');

INSERT INTO centro_empaque (clave, nombre) VALUES
 ('CEMM-TAP', 'Centro de Empaque Tapachula');

INSERT INTO vehiculo (matricula, modelo, capacidad) VALUES
 ('DA-583-E', 'Isuzu ELF refrigerado', 30000000),
 ('DA-214-K', 'Isuzu ELF refrigerado', 30000000),
 ('DB-102-A', 'NP300 refrigerada', 12000000);

INSERT INTO zona_liberacion (clave, nombre, municipio, superficie_ha, densidad_objetivo) VALUES
 ('ZL-001', 'Metapa sector 1', 'Metapa', 8920, 3000),
 ('ZL-005', 'La Trinitaria sector 1', 'La Trinitaria', 8394, 3000),
 ('ZL-016', 'Chicomuselo sector 1', 'Chicomuselo', 5558, 3000),
 ('ZL-020', 'Tapachula sector 5', 'Tapachula', 8173, 3000),
 ('ZL-037', 'Amatenango sector 2', 'Amatenango de la Frontera', 2234, 3000),
 ('ZL-046', 'Villa Comaltitlán sector 1', 'Villa Comaltitlán', 6110, 3000),
 ('ZL-084', 'Tapachula sector 3', 'Tapachula', 6269, 3000),
 ('ZL-087', 'Motozintla sector 4', 'Motozintla', 4523, 3000);

INSERT INTO lote_produccion (codigo, fecha_produccion, fecha_irradiacion, dosis_gy, cantidad_pupas, cepa, tinte, centro_empaque_id, responsable_id)
SELECT v.codigo, current_date - v.d, current_date - v.d + 1, 120.0, v.pupas, v.cepa, 'Rosa fluorescente',
       (SELECT id FROM centro_empaque WHERE clave = 'CEMM-TAP'),
       (SELECT id FROM responsable WHERE num_empleado = 'MET-0003')
FROM (VALUES
 ('MET-2026-0075', 26, 41095000, 'Tapachula-7 TSL'),
 ('MET-2026-0077', 24, 44226000, 'Tapachula-7 TSL'),
 ('MET-2026-0084', 19, 58640000, 'Tapachula-7 TSL'),
 ('MET-2026-0088', 15, 49107000, 'VIENNA-8 TSL'),
 ('MET-2026-0098', 5, 55312000, 'Tapachula-7 TSL'),
 ('MET-2026-0099', 4, 47880000, 'Tapachula-7 TSL'),
 ('MET-2026-0100', 3, 51204000, 'Tapachula-7 TSL')
) AS v(codigo, d, pupas, cepa);

INSERT INTO control_calidad (lote_id, etapa, emergencia, vuelo, esterilidad, peso_mg, fecha, responsable_id)
SELECT l.id, 'PLANTA', v.em, v.vu, 99.9, 7.85, l.fecha_irradiacion + 1, (SELECT id FROM responsable WHERE num_empleado = 'MET-0001')
FROM (VALUES ('MET-2026-0075', 86.0, 77.0), ('MET-2026-0077', 81.0, 58.2),
             ('MET-2026-0084', 86.4, 78.1), ('MET-2026-0088', 85.1, 74.0)) AS v(codigo, em, vu)
JOIN lote_produccion l ON l.codigo = v.codigo;

-- Traslados terrestres Metapa -> Tapachula; el último sigue en camino.
INSERT INTO envio (codigo, lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id, recibido)
SELECT v.codigo, (SELECT id FROM lote_produccion WHERE codigo = v.lote), (SELECT id FROM vehiculo WHERE matricula = v.veh),
       current_date - v.d + time '07:30', v.cant, v.temp, (SELECT id FROM responsable WHERE num_empleado = 'TAP-0008'),
       CASE WHEN v.llego THEN current_date - v.d + time '09:00' END
FROM (VALUES
 ('TAP-ENV-0090', 'MET-2026-0075', 'DA-583-E', 22, 25000000, 3.1, true),
 ('TAP-ENV-0091', 'MET-2026-0084', 'DA-583-E', 15, 25426000, 3.3, true),
 ('TAP-ENV-0094', 'MET-2026-0084', 'DA-214-K', 14, 23511000, 3.0, true),
 ('TAP-ENV-0100', 'MET-2026-0088', 'DA-583-E', 0, 24800000, 3.1, false)
) AS v(codigo, lote, veh, d, cant, temp, llego);

-- Embarques aéreos de Perú en el aeropuerto de Tapachula: una parte se queda y el resto va al centro de empaque.
INSERT INTO embarque_aereo (codigo, guia_aerea, vuelo, fecha_llegada, cantidad, queda_aeropuerto, temperatura, responsable_id)
SELECT v.codigo, v.guia, v.vuelo, current_date - v.d + time '05:40', v.cant, v.queda, 4.0,
       (SELECT id FROM responsable WHERE num_empleado = 'TAP-0030')
FROM (VALUES
 ('AER-EMB-0001', '145-22310455', 'LA2464', 6, 40000000, 15000000),
 ('AER-EMB-0002', '145-22904417', 'LA2464', 1, 38000000, 20000000)
) AS v(codigo, guia, vuelo, d, cant, queda);

-- Informes de dispersión aérea capturados por la encargada de dispersión.
INSERT INTO liberacion (envio_id, embarque_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m, avioneta, piloto, responsable_id)
SELECT (SELECT id FROM envio WHERE codigo = v.fuente), (SELECT id FROM embarque_aereo WHERE codigo = v.fuente), v.punto,
       (SELECT id FROM zona_liberacion WHERE clave = v.zona),
       coalesce((SELECT recibido FROM envio WHERE codigo = v.fuente), (SELECT fecha_llegada FROM embarque_aereo WHERE codigo = v.fuente)) + interval '90 minutes',
       v.cant, v.sup, 2300, v.avioneta, v.piloto, (SELECT id FROM responsable WHERE num_empleado = 'TAP-0021')
FROM (VALUES
 ('TAP-ENV-0090', 'EMPAQUE', 'ZL-046', 25000000, 6110, 'XB-TWE', 'Cap. Ramiro Gálvez'),
 ('TAP-ENV-0091', 'EMPAQUE', 'ZL-020', 24401000, 8173, 'XB-TWE', 'Cap. Ramiro Gálvez'),
 ('AER-EMB-0001', 'AEROPUERTO', 'ZL-037', 15000000, 2234, 'XB-ZKA', 'Cap. Elena Ochoa'),
 ('AER-EMB-0001', 'EMPAQUE', 'ZL-087', 10000000, 4523, 'XB-ZKA', 'Cap. Elena Ochoa')
) AS v(fuente, punto, zona, cant, sup, avioneta, piloto);
