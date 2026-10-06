# Datos

El backend carga los `.txt` reales de `PaqRoute-6F/data` al arrancar y los guarda en repositorios
en memoria. No hay base de datos.

## Fuentes

| Fuente | Archivos | Formato |
|---|---|---|
| Ventas | `ventas.aaaamm.txt` (36) | `##d##h##m:posX,posY,cIdCliente,qq,hl` |
| Bloqueos | `bloqueo.aamm.txt` (36) | `##d##h##m-##d##h##m:x1,y1,x2,y2,...,xn,yn` |
| Mantenimiento | `mant.preventivo.aa.m1-m2.txt` (18) | `aaaammdd:TTNN` (vehículos) |

## GET /api/datos/resumen

Totales de lo cargado:

```json
{
  "totalPedidosVentas": 160010,
  "totalVehiculos": 37,
  "totalAlmacenes": 3,
  "totalBloqueos": 21725,
  "totalMantenimientos": 666
}
```

## GET /api/datos/bloqueos/vigentes?momento=2026-01-01T03:00:00

Cantidad de bloqueos vigentes en un instante (si `momento` se omite, usa ahora):

```json
{ "momento": "2026-01-01T03:00:00", "vigentes": 2 }
```