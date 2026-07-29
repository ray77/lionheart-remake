import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a list of class names into a registry that calls constructors without reflection.
 *
 * Runs on a desktop JVM, where reflection is available, and writes Java source,
 * something TeaVM can translate: class name -> lambda calling the constructor directly.
 */
public final class GenRegistry {

    public static void main(String[] args) throws Exception {
        List<String> names = Files.readAllLines(Path.of(args[0]));
        Path out = Path.of(args[1]);

        StringBuilder body = new StringBuilder();
        StringBuilder nameMap = new StringBuilder();
        int ok = 0;
        int skipped = 0;
        List<String> problems = new ArrayList<>();

        for (String name : names) {
            name = name.trim();
            if (name.isEmpty()) {
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName(name, false, GenRegistry.class.getClassLoader());
            } catch (Throwable t) {
                problems.add(name + " (not found)");
                skipped++;
                continue;
            }
            if (Modifier.isPublic(type.getModifiers())) {
                nameMap.append("        BY_NAME.put(\"").append(type.getName()).append("\", ")
                       .append(type.getCanonicalName()).append(".class);\n");
            }
            if (!Modifier.isPublic(type.getModifiers())) {
                skipped++;
                continue;
            }
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                skipped++;
                continue;
            }
            for (Constructor<?> c : type.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                StringBuilder types = new StringBuilder();
                StringBuilder cast = new StringBuilder();
                boolean usable = true;
                for (int i = 0; i < p.length; i++) {
                    if (p[i].isPrimitive()) {
                        usable = false;
                        break;
                    }
                    if (i > 0) {
                        types.append(", ");
                        cast.append(", ");
                    }
                    types.append(p[i].getCanonicalName()).append(".class");
                    cast.append('(').append(p[i].getCanonicalName()).append(") a[").append(i).append(']');
                }
                if (!usable) {
                    continue;
                }
                body.append("        add(").append(type.getCanonicalName()).append(".class, new Class<?>[] {")
                    .append(types).append("}, a -> new ").append(type.getCanonicalName())
                    .append('(').append(cast).append("));\n");
                ok++;
            }
        }

        StringBuilder src = new StringBuilder();
        src.append("package com.b3dgs.lionengine.web;\n\n")
           .append("import java.util.ArrayList;\nimport java.util.Arrays;\nimport java.util.HashMap;\n")
           .append("import java.util.List;\nimport java.util.Map;\nimport java.util.function.Function;\n\n")
           .append("/**\n")
           .append(" * Constructor registry - GENERATED, do not edit by hand (tools/GenRegistry.java).\n")
           .append(" *\n")
           .append(" * <p>\n")
           .append(" * TeaVM does not allow Constructor.newInstance() on arbitrary classes, while LionEngine\n")
           .append(" * creates game objects from class names read out of XML. This registry holds a lambda\n")
           .append(" * for every known constructor, calling it directly - translatable ahead of time and\n")
           .append(" * without reflection.\n")
           .append(" * </p>\n")
           .append(" */\n")
           .append("public final class ReflectRegistry {\n\n")
           .append("    /** Entry: parameter types and the matching maker. */\n")
           .append("    private static final class Entry {\n")
           .append("        final Class<?>[] types;\n")
           .append("        final Function<Object[], Object> maker;\n\n")
           .append("        Entry(Class<?>[] types, Function<Object[], Object> maker) {\n")
           .append("            this.types = types;\n")
           .append("            this.maker = maker;\n")
           .append("        }\n")
           .append("    }\n\n")
           .append("    private static final Map<Class<?>, List<Entry>> ENTRIES = new HashMap<>();\n\n")
           .append("    private static void add(Class<?> type, Class<?>[] types, Function<Object[], Object> maker) {\n")
           .append("        ENTRIES.computeIfAbsent(type, k -> new ArrayList<>()).add(new Entry(types, maker));\n")
           .append("    }\n\n")
           .append("    /**\n")
           .append("     * @param type The wanted class.\n")
           .append("     * @param params The arguments.\n")
           .append("     * @return The instance, or <code>null</code> when no entry fits.\n")
           .append("     */\n")
           .append("    public static Object create(Class<?> type, Object[] params) {\n")
           .append("        final List<Entry> list = ENTRIES.get(type);\n")
           .append("        if (list == null) {\n")
           .append("            return null;\n")
           .append("        }\n")
           .append("        for (final Entry e : list) {\n")
           .append("            if (e.types.length != params.length) {\n")
           .append("                continue;\n")
           .append("            }\n")
           .append("            boolean fits = true;\n")
           .append("            for (int i = 0; i < params.length; i++) {\n")
           .append("                if (params[i] != null && !e.types[i].isInstance(params[i])) {\n")
           .append("                    fits = false;\n")
           .append("                    break;\n")
           .append("                }\n")
           .append("            }\n")
           .append("            if (fits) {\n")
           .append("                return e.maker.apply(params);\n")
           .append("            }\n")
           .append("        }\n")
           .append("        return null;\n")
           .append("    }\n\n")
           .append("    /**\n")
           .append("     * @param type The class.\n")
           .append("     * @return The known parameter type lists, longest first.\n")
           .append("     */\n")
           .append("    public static List<Class<?>[]> signatures(Class<?> type) {\n")
           .append("        final List<Entry> list = ENTRIES.get(type);\n")
           .append("        final List<Class<?>[]> out = new ArrayList<>();\n")
           .append("        if (list != null) {\n")
           .append("            for (final Entry e : list) {\n")
           .append("                out.add(e.types);\n")
           .append("            }\n")
           .append("            out.sort((a, b) -> b.length - a.length);\n")
           .append("        }\n")
           .append("        return out;\n")
           .append("    }\n\n")
           .append("    /** @return The number of registered constructors. */\n")
           .append("    public static int size() {\n")
           .append("        int n = 0;\n")
           .append("        for (final List<Entry> l : ENTRIES.values()) {\n")
           .append("            n += l.size();\n")
           .append("        }\n")
           .append("        return n;\n")
           .append("    }\n\n")
           .append("    /** Class name -> class, for resolution without a ClassLoader. */\n")
           .append("    private static final Map<String, Class<?>> BY_NAME = new HashMap<>();\n\n")
           .append("    /**\n")
           .append("     * @param name The fully qualified class name.\n")
           .append("     * @return The class, or <code>null</code> when unknown.\n")
           .append("     */\n")
           .append("    public static Class<?> resolve(String name) {\n")
           .append("        return BY_NAME.get(name);\n")
           .append("    }\n\n")
           .append("    static {\n")
           .append(nameMap.toString())
           .append(body)
           .append("    }\n\n")
           .append("    private ReflectRegistry() {\n")
           .append("        // Utility class\n")
           .append("    }\n")
           .append("}\n");

        Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.print(src);
        }
        System.out.println("registered: " + ok + " constructors, skipped: " + skipped);
        if (!problems.isEmpty()) {
            System.out.println("not found: " + problems.size());
            problems.stream().limit(5).forEach(p -> System.out.println("  " + p));
        }
    }
}
