package ua.kpi.d3;

import com.stetsenko.petriobjlib.PetriObjModel;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LibraryVariant {
    public static final String ORIGINAL = "original";
    public static final String MODIFIED = "modified";

    private LibraryVariant() {
    }

    public static String loaded() {
        String location = location(PetriObjModel.class).toString();
        return location.contains("PetriObjLib-modified") || location.contains("petriobjlib-modified") ? MODIFIED : ORIGINAL;
    }

    public static String describe(String variant) {
        return MODIFIED.equals(variant) ? "модифікована" : "оригінальна";
    }

    public static String describeLoaded() {
        return describe(loaded());
    }

    public static Path modelLocation() {
        return location(LibraryVariant.class);
    }

    public static Path projectRoot() {
        Path p = modelLocation().toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("PetriObjLib-master"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Не знайдено каталог проєкту з модулем PetriObjLib-master");
        }
        return p;
    }

    public static Path libraryLocation(String variant) {
        Path module = projectRoot().resolve(MODIFIED.equals(variant) ? "PetriObjLib-modified" : "PetriObjLib-master");
        Path jar = module.resolve("target").resolve(MODIFIED.equals(variant) ? "petriobjlib-modified-1.0.jar" : "petriobjlib-1.0.jar");
        if (Files.exists(jar)) {
            return jar;
        }
        Path classes = module.resolve("target").resolve("classes");
        if (Files.isDirectory(classes)) {
            return classes;
        }
        throw new IllegalStateException("Бібліотеку '" + variant + "' не зібрано: виконайте  mvn package  у каталозі " + projectRoot());
    }

    public static List<String> javaCommand(String variant, List<String> args) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();

        String cp = libraryLocation(variant) + File.pathSeparator + modelLocation();
        List<String> cmd = new ArrayList<>(List.of(java, "-Dd3.relaunched=true", "-cp", cp, Main.class.getName()));
        cmd.addAll(args);
        return cmd;
    }

    public static int relaunch(String variant, List<String> args) {
        try {
            Process p = new ProcessBuilder(javaCommand(variant, args)).inheritIO().start();
            return p.waitFor();
        } catch (Exception e) {
            throw new IllegalStateException("Не вдалося запустити програму з бібліотекою " + variant, e);
        }
    }

    private static Path location(Class<?> c) {
        try {
            return Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
