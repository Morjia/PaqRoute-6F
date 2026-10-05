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
 * Lee el archivo mensual de bloqueos de calles:
 * Nombre: bloqueo.aamm.txt   (aa = anio, 26/27/28; mm = mes)
 * Registro: ##d##h##m-##d##h##m:x1,y1,x2,y2,...,xn,yn
 */
public final class LectorBloqueos {

    private static final Pattern PATRON_ARCHIVO = Pattern.compile("bloqueo\\.(\\d{2})(\\d{2})\\.txt");
    private static final Pattern PATRON_REGISTRO =
            Pattern.compile("(\\d+)d(\\d+)h(\\d+)m-(\\d+)d(\\d+)h(\\d+)m:(.+)");

    private LectorBloqueos() {}

    public static List<Bloqueo> leerArchivo(Path archivo) throws IOException {
        Matcher mArchivo = PATRON_ARCHIVO.matcher(archivo.getFileName().toString());
        if (!mArchivo.matches()) {
            throw new IllegalArgumentException("Nombre de archivo invalido (se esperaba bloqueo.aamm.txt): " + archivo);
        }
        YearMonth mes = YearMonth.of(2000 + Integer.parseInt(mArchivo.group(1)), Integer.parseInt(mArchivo.group(2)));

        List<Bloqueo> bloqueos = new ArrayList<>();
        for (String linea : Files.readAllLines(archivo)) {
            String limpia = linea.trim();
            if (limpia.isEmpty()) continue;
            Matcher m = PATRON_REGISTRO.matcher(limpia);
            if (!m.matches()) {
                throw new IllegalArgumentException("Registro de bloqueo con formato invalido: " + linea);
            }
            LocalDateTime inicio = mes.atDay(Integer.parseInt(m.group(1)))
                    .atTime(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            LocalDateTime fin = mes.atDay(Integer.parseInt(m.group(4)))
                    .atTime(Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));

            String[] coords = m.group(7).split(",");
            List<Punto> poligonal = new ArrayList<>();
            for (int i = 0; i < coords.length; i += 2) {
                poligonal.add(new Punto(Integer.parseInt(coords[i].trim()), Integer.parseInt(coords[i + 1].trim())));
            }
            bloqueos.add(new Bloqueo(inicio, fin, poligonal));
        }
        return bloqueos;
    }

    /** Lee todos los archivos bloqueo.aamm.txt de una carpeta. */
    public static List<Bloqueo> leerCarpeta(Path carpeta) throws IOException {
        List<Bloqueo> todos = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(carpeta)) {
            List<Path> ordenados = archivos
                    .filter(p -> PATRON_ARCHIVO.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path archivo : ordenados) todos.addAll(leerArchivo(archivo));
        }
        return todos;
    }
}
