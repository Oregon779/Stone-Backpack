package dev.stonebackpack.plugin.config;

import dev.stonebackpack.plugin.util.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps a YAML file in the plugin folder in step with the default bundled in the jar.
 * <p>
 * Options missing from the server's file are inserted as text, with their comments, right
 * after the option that precedes them in the default. All other lines stay exactly as they
 * were, so values, comments, blank lines, order and options the plugin doesn't know survive.
 * The result is re-read before it is written: if any existing value would change (or the
 * file uses a layout the text approach can't handle), Bukkit's comment-preserving API adds
 * the options instead - values and comments survive, but the file is reformatted.
 * <p>
 * Renamed, moved or removed options need a {@link ConfigMigration}: files with a version key
 * run the pending steps first.
 */
public final class ConfigUpdater {

    /**
     * @param versionKey          top-level key holding the file's layout version, or null for unversioned files
     * @param userManagedSections sections whose entries the server owner defines; once the section
     *                            exists, entries missing from it are not added back
     */
    public record Options(String versionKey, List<ConfigMigration> migrations, Set<String> userManagedSections) {
        public static Options plain() {
            return new Options(null, List.of(), Set.of());
        }
    }

    public record Result(List<String> addedPaths, List<ConfigMigration> appliedMigrations, boolean changed) {
        static Result unchanged() {
            return new Result(List.of(), List.of(), false);
        }
    }

    record Merge(String text, List<String> addedPaths, boolean reformatted) {
    }

    private record Migration(String text, List<ConfigMigration> applied, int version) {
    }

    private record Block(List<String> lines, boolean blankBefore) {
    }

    private ConfigUpdater() {
    }

    public static Result update(Plugin plugin, String resourcePath, File target, Options options) {
        return update(plugin, resourcePath, resourcePath, target, options);
    }

    /**
     * @param defaultsResource bundled file that provides the defaults; differs from
     *                         {@code displayName} when a file has no bundled counterpart of its own
     */
    public static Result update(Plugin plugin, String defaultsResource, String displayName, File target, Options options) {
        try (InputStream stream = plugin.getResource(defaultsResource)) {
            if (stream == null) {
                plugin.getLogger().warning(defaultsResource + " is missing from the plugin jar, so " + displayName + " was not updated.");
                return Result.unchanged();
            }
            String defaults = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return update(defaults, target, displayName, options, plugin.getLogger());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not update " + displayName + "; it was left unchanged.", exception);
            return Result.unchanged();
        }
    }

    static Result update(String defaultsText, File target, String displayName, Options options, Logger logger) throws IOException {
        String original = Files.readString(target.toPath(), StandardCharsets.UTF_8);
        Migration migration;
        Merge merge;
        try {
            migration = options.versionKey() == null ? new Migration(original, List.of(), 0) : migrate(defaultsText, original, options);
            if (migration == null) {
                logger.warning(displayName + " was written by a newer version of the plugin (" + options.versionKey()
                        + " is higher than this version knows). It was left unchanged.");
                return Result.unchanged();
            }
            merge = merge(defaultsText, migration.text(), options.userManagedSections());
        } catch (InvalidYamlException exception) {
            logger.warning(displayName + " could not be read (" + exception.getMessage() + "). It was left unchanged, and "
                    + "options that can't be read use their default values until the file is fixed.");
            return Result.unchanged();
        }

        if (merge.text().equals(original)) {
            return Result.unchanged();
        }
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        AtomicFiles.write(backup.toPath(), original, true);
        AtomicFiles.write(target.toPath(), merge.text(), true);

        if (!migration.applied().isEmpty()) {
            List<String> steps = migration.applied().stream().map(ConfigMigration::description).toList();
            logger.info("Updated " + displayName + " to " + options.versionKey() + " " + migration.version() + ": "
                    + String.join("; ", steps) + ".");
        }
        if (!merge.addedPaths().isEmpty()) {
            logger.info("Added " + merge.addedPaths().size() + " new option(s) to " + displayName + ": "
                    + String.join(", ", merge.addedPaths()) + ". Existing settings were kept.");
        }
        if (merge.reformatted() || !migration.applied().isEmpty()) {
            logger.info(displayName + " was rewritten in the standard layout; all values and comments were kept.");
        }
        logger.info("The previous " + displayName + " was saved as " + backup.getName() + ".");
        return new Result(merge.addedPaths(), migration.applied(), true);
    }

    // Returns null when the file is newer than the bundled default.
    private static Migration migrate(String defaultsText, String text, Options options) throws InvalidYamlException {
        String key = options.versionKey();
        int latest = load(defaultsText).getInt(key, 0);
        YamlConfiguration current = load(text);
        int version = current.getInt(key, 0);
        if (version > latest) {
            return null;
        }
        if (version == latest) {
            return new Migration(text, List.of(), latest);
        }
        List<ConfigMigration> pending = options.migrations().stream()
                .filter(step -> step.version() > version && step.version() <= latest)
                .sorted(Comparator.comparingInt(ConfigMigration::version))
                .toList();
        if (pending.isEmpty()) {
            // Only the number moves, so the file keeps its exact layout. A missing
            // key is added with the latest version by the merge.
            String bumped = current.contains(key) ? replaceTopLevelValue(text, key, latest) : text;
            if (bumped != null) {
                return new Migration(bumped, List.of(), latest);
            }
        }
        for (ConfigMigration step : pending) {
            step.action().accept(current);
        }
        current.set(key, latest);
        return new Migration(current.saveToString(), pending, latest);
    }

    private static String replaceTopLevelValue(String text, String key, int value) {
        Matcher matcher = Pattern.compile("(?m)^(" + Pattern.quote(key) + "[ \\t]*:[ \\t]*)[^#\\r\\n]*?([ \\t]*#.*)?$").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String comment = matcher.group(2) == null ? "" : matcher.group(2);
        return text.substring(0, matcher.start()) + matcher.group(1) + value + comment + text.substring(matcher.end());
    }

    static Merge merge(String defaultsText, String userText, Set<String> userManaged) throws InvalidYamlException {
        YamlText user = YamlText.parse(userText);
        YamlText defaults = YamlText.parse(defaultsText);
        try {
            Merge merge = mergeAsText(defaults, user, userText, defaultsText, userManaged);
            verify(userText, defaultsText, merge);
            return merge;
        } catch (FallbackException exception) {
            return mergeWithBukkit(defaultsText, userText, userManaged);
        }
    }

    private static Merge mergeAsText(YamlText defaults, YamlText user, String userText, String defaultsText,
                                     Set<String> userManaged) throws InvalidYamlException, FallbackException {
        if (!(defaults.root instanceof MappingNode defaultRoot)) {
            return new Merge(userText, List.of(), false);
        }
        List<String> topLevel = defaultRoot.getValue().stream().map(ConfigUpdater::keyOf).filter(Objects::nonNull).toList();
        if (user.root == null) {
            // Empty, or nothing but comments: the whole default goes below them.
            if (userText.isBlank()) {
                return new Merge(defaultsText, topLevel, false);
            }
            Insertions insertions = new Insertions();
            insertions.add(user.lines.size(), new Block(defaults.lines, true), topLevel);
            return new Merge(user.render(insertions), topLevel, false);
        }
        if (!(user.root instanceof MappingNode userRoot)) {
            throw new InvalidYamlException("the top level is not a list of options");
        }
        Insertions insertions = new Insertions();
        mergeMapping(defaults, defaultRoot, user, userRoot, "", userManaged, insertions);
        return new Merge(user.render(insertions), insertions.paths, false);
    }

    private static void mergeMapping(YamlText defaults, MappingNode defaultMap, YamlText user, MappingNode userMap,
                                     String prefix, Set<String> userManaged, Insertions insertions) throws FallbackException {
        Map<String, NodeTuple> existing = new HashMap<>();
        for (NodeTuple tuple : userMap.getValue()) {
            String key = keyOf(tuple);
            if (key != null) {
                existing.putIfAbsent(key, tuple);
            }
        }

        // Insertion line for the next missing option: right after its closest
        // preceding sibling (in default order) that the server's file has.
        Integer anchor = null;
        for (NodeTuple defaultTuple : defaultMap.getValue()) {
            String key = keyOf(defaultTuple);
            if (key == null) {
                continue;
            }
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            NodeTuple userTuple = existing.get(key);
            if (userTuple != null) {
                anchor = user.lastLine(userTuple) + 1;
                if (defaultTuple.getValueNode() instanceof MappingNode defaultChild
                        && userTuple.getValueNode() instanceof MappingNode userChild
                        && !userManaged.contains(path)) {
                    mergeMapping(defaults, defaultChild, user, userChild, path, userManaged, insertions);
                }
                continue;
            }
            if (userMap.getFlowStyle() == DumperOptions.FlowStyle.FLOW || userMap.getValue().isEmpty()) {
                throw new FallbackException();
            }
            int index = anchor != null ? anchor : user.firstChildInsertIndex(userMap);
            insertions.add(index, defaults.block(defaultTuple, user.indent(userMap)), List.of(path));
        }
    }

    private static void verify(String before, String defaultsText, Merge merge) throws FallbackException {
        try {
            YamlConfiguration old = load(before);
            YamlConfiguration merged = load(merge.text());
            YamlConfiguration defaults = load(defaultsText);
            for (String path : old.getKeys(true)) {
                if (!old.isConfigurationSection(path) && !Objects.equals(old.get(path), merged.get(path))) {
                    throw new FallbackException();
                }
            }
            for (String path : merge.addedPaths()) {
                if (defaults.contains(path) && !merged.contains(path)) {
                    throw new FallbackException();
                }
            }
        } catch (InvalidYamlException exception) {
            throw new FallbackException();
        }
    }

    private static Merge mergeWithBukkit(String defaultsText, String userText, Set<String> userManaged) throws InvalidYamlException {
        YamlConfiguration defaults = load(defaultsText);
        YamlConfiguration config = load(userText);
        List<String> missing = new ArrayList<>();
        for (String path : defaults.getKeys(true)) {
            if (!config.contains(path) && !isBelowAny(missing, path) && !hasNonSectionParent(config, path)
                    && !isInUserManagedSection(config, path, userManaged)) {
                missing.add(path);
            }
        }
        if (missing.isEmpty()) {
            return new Merge(userText, List.of(), false);
        }
        for (String path : missing) {
            copy(defaults, config, path);
            ConfigurationSection section = defaults.getConfigurationSection(path);
            if (section != null) {
                for (String child : section.getKeys(true)) {
                    copy(defaults, config, path + "." + child);
                }
            }
        }
        return new Merge(config.saveToString(), missing, true);
    }

    private static void copy(YamlConfiguration from, YamlConfiguration to, String path) {
        if (from.isConfigurationSection(path)) {
            to.createSection(path);
        } else {
            to.set(path, from.get(path));
        }
        to.setComments(path, from.getComments(path));
        to.setInlineComments(path, from.getInlineComments(path));
    }

    private static boolean isBelowAny(List<String> paths, String path) {
        return paths.stream().anyMatch(parent -> path.startsWith(parent + "."));
    }

    // A server owner who turned a section into a plain value keeps it that way.
    private static boolean hasNonSectionParent(YamlConfiguration config, String path) {
        for (int dot = path.indexOf('.'); dot >= 0; dot = path.indexOf('.', dot + 1)) {
            String parent = path.substring(0, dot);
            if (config.contains(parent) && !config.isConfigurationSection(parent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInUserManagedSection(YamlConfiguration config, String path, Set<String> userManaged) {
        return userManaged.stream().anyMatch(section -> path.startsWith(section + ".") && config.isConfigurationSection(section));
    }

    private static YamlConfiguration load(String text) throws InvalidYamlException {
        YamlConfiguration yaml = new YamlConfiguration();
        // Long texts would otherwise be folded onto several lines when saved.
        yaml.options().width(Integer.MAX_VALUE);
        try {
            yaml.loadFromString(text.startsWith(YamlText.BOM) ? text.substring(1) : text);
        } catch (InvalidConfigurationException exception) {
            throw new InvalidYamlException(exception.getMessage());
        }
        return yaml;
    }

    private static String keyOf(NodeTuple tuple) {
        return tuple.getKeyNode() instanceof ScalarNode scalar ? scalar.getValue() : null;
    }

    private static final class Insertions {
        private final TreeMap<Integer, List<Block>> byLine = new TreeMap<>();
        private final List<String> paths = new ArrayList<>();

        private void add(int line, Block block, List<String> addedPaths) {
            byLine.computeIfAbsent(line, ignored -> new ArrayList<>()).add(block);
            paths.addAll(addedPaths);
        }
    }

    /** The file as lines, plus SnakeYAML's node tree whose marks point into those lines. */
    private static final class YamlText {
        private static final String BOM = "﻿";

        private final List<String> lines;
        private final String separator;
        private final boolean trailingNewline;
        private final boolean bom;
        private final Node root;

        private YamlText(List<String> lines, String separator, boolean trailingNewline, boolean bom, Node root) {
            this.lines = lines;
            this.separator = separator;
            this.trailingNewline = trailingNewline;
            this.bom = bom;
            this.root = root;
        }

        private static YamlText parse(String content) throws InvalidYamlException {
            boolean bom = content.startsWith(BOM);
            String body = bom ? content.substring(1) : content;
            Node root;
            try {
                root = new Yaml(new LoaderOptions()).compose(new StringReader(body));
            } catch (YAMLException exception) {
                throw new InvalidYamlException(describe(exception));
            }
            List<String> lines = new ArrayList<>(Arrays.asList(body.split("\r?\n", -1)));
            boolean trailingNewline = lines.size() > 1 && lines.get(lines.size() - 1).isEmpty();
            if (trailingNewline) {
                lines.remove(lines.size() - 1);
            }
            return new YamlText(lines, body.contains("\r\n") ? "\r\n" : "\n", trailingNewline, bom, root);
        }

        private static String describe(YAMLException exception) {
            if (exception instanceof MarkedYAMLException marked && marked.getProblemMark() != null) {
                return "line " + (marked.getProblemMark().getLine() + 1) + ": " + marked.getProblem();
            }
            return exception.getMessage();
        }

        private String render(Insertions insertions) {
            List<String> result = new ArrayList<>(lines);
            for (Map.Entry<Integer, List<Block>> entry : insertions.byLine.descendingMap().entrySet()) {
                int index = entry.getKey();
                String previous = index > 0 ? result.get(index - 1) : null;
                List<String> added = new ArrayList<>();
                for (Block block : entry.getValue()) {
                    if (block.blankBefore() && previous != null && !previous.isBlank()) {
                        added.add("");
                    }
                    added.addAll(block.lines());
                    if (!block.lines().isEmpty()) {
                        previous = block.lines().get(block.lines().size() - 1);
                    }
                }
                result.addAll(index, added);
            }
            return (bom ? BOM : "") + String.join(separator, result) + (trailingNewline ? separator : "");
        }

        private int indent(MappingNode mapping) {
            return mapping.getValue().getFirst().getKeyNode().getStartMark().getColumn();
        }

        // Before the comments that belong to the mapping's first option.
        private int firstChildInsertIndex(MappingNode mapping) {
            int index = mapping.getValue().getFirst().getKeyNode().getStartMark().getLine();
            while (index > 0 && isComment(index - 1)) {
                index--;
            }
            return index;
        }

        // The option's lines with the comments directly above it, shifted to the target indentation.
        private Block block(NodeTuple tuple, int targetIndent) {
            Mark keyStart = tuple.getKeyNode().getStartMark();
            int start = keyStart.getLine();
            while (start > 0 && isComment(start - 1)) {
                start--;
            }
            boolean blankBefore = start > 0 && lines.get(start - 1).isBlank();
            int shift = targetIndent - keyStart.getColumn();
            List<String> out = new ArrayList<>();
            for (int i = start; i <= lastLine(tuple); i++) {
                out.add(shifted(lines.get(i), shift));
            }
            return new Block(out, blankBefore);
        }

        private int lastLine(NodeTuple tuple) {
            return Math.max(tuple.getKeyNode().getStartMark().getLine(), lastLine(tuple.getValueNode()));
        }

        // Last line holding the node's own content. A block collection's end mark
        // sits at the next option, past any comments in between, so it recurses
        // into the last entry instead.
        private int lastLine(Node node) {
            if (node instanceof MappingNode mapping && mapping.getFlowStyle() != DumperOptions.FlowStyle.FLOW
                    && !mapping.getValue().isEmpty()) {
                return lastLine(mapping.getValue().getLast());
            }
            if (node instanceof SequenceNode sequence && sequence.getFlowStyle() != DumperOptions.FlowStyle.FLOW
                    && !sequence.getValue().isEmpty()) {
                return lastLine(sequence.getValue().getLast());
            }
            Mark start = node.getStartMark();
            Mark end = node.getEndMark();
            int line = end.getLine();
            if (end.getColumn() == 0 && line > start.getLine()) {
                line--;
            }
            if (node instanceof ScalarNode scalar && (scalar.getScalarStyle() == DumperOptions.ScalarStyle.LITERAL
                    || scalar.getScalarStyle() == DumperOptions.ScalarStyle.FOLDED)) {
                while (line > start.getLine() && lines.get(line).isBlank()) {
                    line--;
                }
            }
            return Math.min(line, lines.size() - 1);
        }

        private boolean isComment(int line) {
            return lines.get(line).trim().startsWith("#");
        }

        private static String shifted(String line, int shift) {
            if (shift == 0 || line.isBlank()) {
                return line;
            }
            if (shift > 0) {
                return " ".repeat(shift) + line;
            }
            int leading = 0;
            while (leading < line.length() && line.charAt(leading) == ' ') {
                leading++;
            }
            return line.substring(Math.min(-shift, leading));
        }
    }

    static final class InvalidYamlException extends Exception {
        InvalidYamlException(String message) {
            super(message);
        }
    }

    private static final class FallbackException extends Exception {
    }
}
