package ua.kpi.d3.experiment;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class Output implements AutoCloseable {
    private final Path dir;
    private final PrintStream file;

    public Output(Path dir, String logName) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
            this.file = new PrintStream(Files.newOutputStream(dir.resolve(logName)), true, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося створити каталог результатів " + dir, e);
        }
    }

    public Path dir() {
        return dir;
    }

    public void println(String s) {
        System.out.println(s);
        file.println(s);
    }

    public void print(String s) {
        System.out.print(s);
        file.print(s);
    }

    public void println() {
        println("");
    }

    public void printf(String format, Object... args) {
        String s = String.format(Locale.US, format, args);
        System.out.print(s);
        file.print(s);
    }

    public void section(String title) {
        println();
        println("-".repeat(100));
        println(title);
    }

    public void conclusion(String text) {
        println("Висновок: " + text);
    }

    public void csv(String name, String header, List<String> rows) {
        try {
            StringBuilder sb = new StringBuilder(header).append('\n');
            for (String r : rows) {
                sb.append(r).append('\n');
            }
            Files.writeString(dir.resolve(name), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося записати " + name, e);
        }
    }

    public PrintStream openFile(String name) {
        try {
            return new PrintStream(Files.newOutputStream(dir.resolve(name)), true, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося створити " + name, e);
        }
    }

    public static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            Object v = values[i];
            if (v instanceof Double d) {
                sb.append(String.format(Locale.US, "%.8g", d));
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    @Override
    public void close() {
        file.close();
    }
}
