package lib.minecraft.nbt.io.snbt;

import lombok.experimental.UtilityClass;

/**
 * Shared syntactic constants for SNBT parsing and emission.
 *
 * <p>Used by {@link SnbtSerializer} and {@link SnbtDeserializer} as the single source of truth
 * for the SNBT grammar: structural characters ({@code {}}, {@code []}, {@code ,}, {@code :}),
 * string quote delimiters, array-type prefix letters ({@code B}, {@code I}, {@code L}),
 * numeric-literal suffix letters ({@code b}, {@code s}, {@code l}, {@code f}, {@code d}),
 * and the lookup tables that classify characters allowed in unquoted identifiers. Matching what
 * the Minecraft Wiki's <a href="https://minecraft.wiki/w/NBT_format">"SNBT format"</a> section
 * defines is a copy-paste from this one file.</p>
 *
 * <p>Package-private because the constants are implementation details of the SNBT backend and
 * should not leak into the public API surface.</p>
 */
@UtilityClass
final class SnbtConstants {

    public static final char COMPOUND_START        = '{';
    public static final char COMPOUND_END          = '}';

    public static final char ENTRY_VALUE_INDICATOR = ':';
    public static final char ENTRY_SEPARATOR       = ',';

    public static final char ARRAY_START           = '[';
    public static final char ARRAY_END             = ']';
    public static final char ARRAY_TYPE_INDICATOR  = ';';
    public static final char ARRAY_PREFIX_BYTE     = 'B';
    public static final char ARRAY_PREFIX_INT      = 'I';
    public static final char ARRAY_PREFIX_LONG     = 'L';
    public static final String ARRAY_SUFFIX_BYTE   = "b";
    public static final String ARRAY_SUFFIX_INT    = "";
    public static final String ARRAY_SUFFIX_LONG   = "L";

    public static final char STRING_DELIMITER_1    = '\"';
    public static final char STRING_DELIMITER_2    = '\'';
    public static final char STRING_ESCAPE         = '\\';

    /**
     * ASCII lookup table marking characters that the deserializer accepts inside an unquoted
     * identifier. Mirrors the prior {@code VALID_UNQUOTED_CHARS} string
     * ({@code [A-Za-z0-9._+-]}) byte-for-byte; any code unit {@code >= 128} or absent from the
     * set forces the deserializer to terminate the identifier scan.
     */
    public static final boolean[] IS_VALID_UNQUOTED = buildValidUnquotedTable();

    /**
     * ASCII lookup table marking characters that the serializer is willing to emit unquoted.
     * Mirrors the prior {@code NON_QUOTE_PATTERN} regex ({@code [a-zA-Z_.+\-]}) byte-for-byte:
     * digits are deliberately excluded so that pure-numeric strings such as {@code "127"} stay
     * quoted on emission and round-trip back as {@code StringTag} rather than being reparsed
     * as a numeric tag.
     */
    public static final boolean[] IS_NON_QUOTE = buildNonQuoteTable();

    private static boolean[] buildValidUnquotedTable() {
        boolean[] table = new boolean[128];
        for (char c = 'a'; c <= 'z'; c++) table[c] = true;
        for (char c = 'A'; c <= 'Z'; c++) table[c] = true;
        for (char c = '0'; c <= '9'; c++) table[c] = true;
        table['_'] = true;
        table['.'] = true;
        table['+'] = true;
        table['-'] = true;
        return table;
    }

    private static boolean[] buildNonQuoteTable() {
        boolean[] table = new boolean[128];
        for (char c = 'a'; c <= 'z'; c++) table[c] = true;
        for (char c = 'A'; c <= 'Z'; c++) table[c] = true;
        table['_'] = true;
        table['.'] = true;
        table['+'] = true;
        table['-'] = true;
        return table;
    }

}
