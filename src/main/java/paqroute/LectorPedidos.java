package paqroute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lee el archivo mensual historico/proyectado de envios:
 * Nombre: ventas.aaaamm.txt   (aaaa = anio, mm = mes)
 * Registro: ##d##h##m:posX,posY,cIdCliente,qq,hl
 */
public final class LectorPedidos {

    private static final Pattern PATRON_ARCHIVO = Pattern.compile("ventas\\.(\\d{4})(\\d{2})\\.txt");
    private static final Pattern PATRON_REGISTRO =
            Pattern.compile("(\\d+)d(\\d+)h(\\d+)m:(\\d+),(\\d+),([^,]+),(\\d+),(\\d+)");

    private LectorPedidos() {}

    public static List<Pedido> leerArchivo(Path archivo) throws IOException {
        Matcher mArchivo = PATRON_ARCHIVO.matcher(archivo.getFileName().toString());
        if (!mArchivo.matches()) {
            throw new IllegalArgumentException("Nombre de archivo invalido (se esperaba ventas.aaaamm.txt): " + archivo);
        }
        YearMonth mes = YearMonth.of(Integer.parseInt(mArchivo.group(1)), Integer.parseInt(mArchivo.group(2)));

        List<Pedido> pedidos = new ArrayList<>();
        for (String linea : Files.readAllLines(archivo)) {
            String limpia = linea.trim();
            if (limpia.isEmpty()) continue;
            Matcher m = PATRON_REGISTRO.matcher(limpia);
            if (!m.matches()) {
                throw new IllegalArgumentException("Registro con formato invalido: " + linea);
            }
            int dia = Integer.parseInt(m.group(1));
            int hora = Integer.parseInt(m.group(2));
            int minuto = Integer.parseInt(m.group(3));
            int x = Integer.parseInt(m.group(4));
            int y = Integer.parseInt(m.group(5));
            String cliente = m.group(6);
            int qq = Integer.parseInt(m.group(7));
            double hl = Double.parseDouble(m.group(8));

            LocalDateTime momento = mes.atDay(dia).atTime(hora, minuto);
            pedidos.add(new Pedido(cliente, new Punto(x, y), qq, momento, hl));
        }
        return pedidos;
    }

    /** Lee todos los archivos ventas.aaaamm.txt de una carpeta y devuelve los pedidos en orden cronologico. */
    public static List<Pedido> leerCarpeta(Path carpeta) throws IOException {
        List<Pedido> todos = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(carpeta)) {
            List<Path> ordenados = archivos
                    .filter(p -> PATRON_ARCHIVO.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path archivo : ordenados) todos.addAll(leerArchivo(archivo));
        }
        todos.sort(Comparator.comparing(p -> p.momentoLlegada));
        return todos;
    }
}
