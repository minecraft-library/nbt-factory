package lib.minecraft.nbt.io.snbt;

import dev.simplified.annotations.UtilityClass;

/**
 * Shared syntactic constants for SNBT parsing and emission.
 *
 * <p>Used by {@link SnbtSerializer} and {@link SnbtDeserializer} as the single source of truth
 * for the SNBT grammar: structural characters ({@code {}}, {@code []}, {@code ,}, {@code :}),
 * string quote delimiters, array-type prefix letters ({@code B}, {@code I}, {@code L}), the
 * numeric-literal suffix letters vanilla writes ({@code b}, {@code s}, {@code L}, {@code f},
 * {@code d}, and {@code B} / {@code L} on an array's elements), and the lookup table that
 * classifies characters allowed in unquoted identifiers. A suffix reads back whatever its case.</p>
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
    public static final String ARRAY_SUFFIX_BYTE   = "B";
    public static final String ARRAY_SUFFIX_INT    = "";
    public static final String ARRAY_SUFFIX_LONG   = "L";

    public static final char SUFFIX_BYTE           = 'b';
    public static final char SUFFIX_SHORT          = 's';
    public static final char SUFFIX_LONG           = 'L';
    public static final char SUFFIX_FLOAT          = 'f';
    public static final char SUFFIX_DOUBLE         = 'd';

    public static final char STRING_DELIMITER_1    = '\"';
    public static final char STRING_DELIMITER_2    = '\'';
    public static final char STRING_ESCAPE         = '\\';

    /**
     * Maximum lookahead in characters reserved by {@code SnbtDeserializer.readUTF(true)} when peeking
     * an unquoted token to classify it via {@code peekTagId}.
     *
     * <p>Replaces a prior {@code mark(Integer.MAX_VALUE)} call. {@code BufferedReader} sizes its
     * internal buffer to the {@code mark} limit on call, so an unbounded value would have
     * {@code BufferedReader} pin an unbounded buffer for the duration of the peek. 4 KiB
     * comfortably covers every realistic SNBT identifier, including the 1 KiB+ corner cases that
     * the round-trip suite probes; the NBT wire format implicitly caps identifier length below
     * the {@code unsigned short} ceiling of 32 KiB regardless.</p>
     */
    public static final int MAX_PEEK_LOOKAHEAD     = 4096;

    /**
     * ASCII lookup table marking characters that the deserializer accepts inside an unquoted
     * identifier. Mirrors the prior {@code VALID_UNQUOTED_CHARS} string
     * ({@code [A-Za-z0-9._+-]}) byte-for-byte; any code unit {@code >= 128} or absent from the
     * set forces the deserializer to terminate the identifier scan.
     */
    public static final boolean[] IS_VALID_UNQUOTED = buildValidUnquotedTable();

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

}
