package io.github.headlesshq.headlessmc.it;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A table as printed by headlessmc list commands, e.g. {@code config ls} or {@code java ls}:
 * a header row followed by rows whose cells start at the same column as their header.
 * Columns are separated by at least two spaces, so cells may contain single spaces, e.g. Windows paths.
 * <p>
 * The cells are taken from the raw output by position and normalized afterwards, because normalizing first would
 * shift the columns when a long directory is replaced by a short token. A table with only {@code -} cells is empty.
 *
 * @param columns the header names in order.
 * @param rows    the rows, each mapping header name to normalized cell value.
 */
public record HmcTable(List<String> columns, List<Map<String, String>> rows) {
    private static final Pattern COLUMN = Pattern.compile("(?:^|(?<=\\s{2}))\\S");
    private static final Normalizer LINES = Normalizer.builder().timestamps(false).build();

    public HmcTable {
        columns = List.copyOf(columns);
        rows = rows.stream().map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
    }

    /**
     * Parses the stdout of a command.
     *
     * @param result the result of a list command.
     * @return the table.
     * @throws IllegalArgumentException if the output is empty.
     */
    public static HmcTable parse(CommandResult result) {
        return parse(result.stdout(), result.normalizer());
    }

    /**
     * Parses a table.
     *
     * @param raw        the raw output, starting with the header row.
     * @param normalizer the normalizer applied to each cell.
     * @return the table.
     * @throws IllegalArgumentException if the output is empty.
     */
    public static HmcTable parse(String raw, Normalizer normalizer) {
        List<String> lines = LINES.normalize(raw).lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("No table in output: '" + raw + "'");
        }

        String header = lines.getFirst();
        List<Integer> starts = new ArrayList<>();
        Matcher matcher = COLUMN.matcher(header);
        while (matcher.find()) {
            starts.add(matcher.start());
        }

        List<String> columns = cells(header, starts).stream().map(String::strip).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            List<String> cells = cells(line, starts);
            if (cells.stream().allMatch(cell -> cell.isBlank() || cell.strip().equals("-"))) {
                continue;
            }

            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                row.put(columns.get(i), normalizer.normalize(cells.get(i)).strip());
            }

            rows.add(row);
        }

        return new HmcTable(columns, rows);
    }

    private static List<String> cells(String line, List<Integer> starts) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int start = Math.min(starts.get(i), line.length());
            int end = i + 1 < starts.size() ? Math.min(starts.get(i + 1), line.length()) : line.length();
            cells.add(line.substring(start, Math.max(start, end)));
        }

        return cells;
    }

    /**
     * @param column the header name.
     * @return the values of that column in row order.
     */
    public List<String> column(String column) {
        return rows.stream().map(row -> row.get(column)).toList();
    }

    /**
     * @param column the header name.
     * @param value  the normalized cell value to look for.
     * @return the first row with that value in the column.
     */
    public Optional<Map<String, String>> row(String column, String value) {
        return rows.stream().filter(row -> value.equals(row.get(column))).findFirst();
    }

}
