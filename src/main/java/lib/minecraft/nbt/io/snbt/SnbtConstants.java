package dev.sbs.minecraftapi.nbt.io.snbt;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

/**
 * Shared syntactic constants and regular expressions for SNBT parsing and emission.
 *
 * <p>Used by {@link SnbtSerializer} and {@link SnbtDeserializer} as the single source of truth
 * for the SNBT grammar: structural characters ({@code {}}, {@code []}, {@code ,}, {@code :}),
 * string quote delimiters, array-type prefix letters ({@code B}, {@code I}, {@code L}),
 * numeric-literal suffix regex patterns ({@code b}, {@code s}, {@code l}, {@code f}, {@code d}),
 * and the set of characters allowed in unquoted strings. Matching what the Minecraft Wiki's
 * <a href="https://minecraft.wiki/w/NBT_format">"SNBT format"</a> section defines is a
 * copy-paste from this one file.</p>
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

    public static final Pattern BYTE_PATTERN       = Pattern.compile("^[+-]?\\d+b$", Pattern.CASE_INSENSITIVE);
    public static final Pattern SHORT_PATTERN      = Pattern.compile("^[+-]?\\d+s$", Pattern.CASE_INSENSITIVE);
    public static final Pattern INT_PATTERN        = Pattern.compile("^[+-]?\\d+$", Pattern.CASE_INSENSITIVE);
    public static final Pattern LONG_PATTERN       = Pattern.compile("^[+-]?\\d+l$", Pattern.CASE_INSENSITIVE);
    public static final Pattern FLOAT_PATTERN      = Pattern.compile("^[+-]?[0-9]*\\.?[0-9]+f$", Pattern.CASE_INSENSITIVE);
    public static final Pattern DOUBLE_PATTERN     = Pattern.compile("^[+-]?[0-9]*\\.?[0-9]+d$", Pattern.CASE_INSENSITIVE);
    public static final Pattern NON_QUOTE_PATTERN  = Pattern.compile("[a-zA-Z_.+\\-]+");

    /**
     * Used to find and delete suffixes from numeric literals.
     */
    public static final Pattern LITERAL_SUFFIX_PATTERN = Pattern.compile("[BbDdFfLlSs]$");

    /**
     * All characters that can be used in strings without quotation marks (including tag names).
     */
    public static final String VALID_UNQUOTED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-+_.";

}
