package it.unlocksecurity.burp.screenshotmode.render;

import java.awt.Color;

/** Colors of the captured content, which must look the same whatever Burp's look and feel paints around it. */
public record Palette(
    Color background,
    Color headerBackground,
    Color border,
    Color text,
    Color muted,
    Color codeBackground,
    Color sni,
    Color selection,
    Color method,
    Color path,
    Color version,
    Color headerName,
    Color statusText,
    Color key,
    Color string,
    Color number,
    Color keyword,
    Color tag,
    Color attribute,
    Color punctuation,
    Color comment,
    Color status2xx,
    Color status3xx,
    Color status4xx,
    Color status5xx,
    /** Backdrop of the preview area, outside the captured content. */
    Color canvas,
    /** Opaque redactions and the box of replaced text: clearly a redaction on this background. */
    Color redaction,
    /** Replaced text on its {@link #redaction} box, with at least 4.5:1 contrast. */
    Color redactionText
) {
    public static final Palette DARK = new Palette(
        new Color(0x23252b),
        new Color(0x2b2e35),
        new Color(0x3d414b),
        new Color(0xe6e8ec),
        new Color(0x8b909c),
        new Color(0x373b45),
        new Color(0xfbbf24),
        new Color(0x3f5a8a),
        new Color(0xc792ea),
        new Color(0xe6e8ec),
        new Color(0x7fd1a0),
        new Color(0xf2c55c),
        new Color(0xc792ea),
        new Color(0x82aaff),
        new Color(0xa5d6a7),
        new Color(0xf78c6c),
        new Color(0xc792ea),
        new Color(0x82aaff),
        new Color(0xf2c55c),
        new Color(0x7a8090),
        new Color(0x6b7280),
        new Color(0x7fd1a0),
        new Color(0x82aaff),
        new Color(0xf2c55c),
        new Color(0xf07178),
        new Color(0x18191d),
        new Color(0x808080),
        new Color(0x000000)
    );

    public static final Palette LIGHT = new Palette(
        new Color(0xffffff),
        new Color(0xf4f5f7),
        new Color(0xd7dae0),
        new Color(0x1f2328),
        new Color(0x6e7781),
        new Color(0xeaecef),
        new Color(0xb45309),
        new Color(0xb6d3ff),
        new Color(0x8250df),
        new Color(0x1f2328),
        new Color(0x1a7f37),
        new Color(0x9a6700),
        new Color(0x8250df),
        new Color(0x0550ae),
        new Color(0x0a3069),
        new Color(0x953800),
        new Color(0xcf222e),
        new Color(0x116329),
        new Color(0x953800),
        new Color(0x6e7781),
        new Color(0x6e7781),
        new Color(0x1a7f37),
        new Color(0x0550ae),
        new Color(0x9a6700),
        new Color(0xcf222e),
        new Color(0xe4e6ea),
        new Color(0x000000),
        new Color(0xffffff)
    );

    public Color forToken(HttpSyntax.TokenType type, String tokenText) {
        return switch (type) {
            case METHOD -> method;
            case PATH -> path;
            case VERSION -> version;
            case STATUS_CODE -> forStatus(tokenText);
            case STATUS_TEXT -> statusText;
            case HEADER_NAME -> headerName;
            case KEY -> key;
            case STRING -> string;
            case NUMBER -> number;
            case KEYWORD -> keyword;
            case TAG -> tag;
            case ATTRIBUTE -> attribute;
            case PUNCTUATION -> punctuation;
            case COMMENT -> comment;
        };
    }

    private Color forStatus(String code) {
        return switch (code.isEmpty() ? ' ' : code.charAt(0)) {
            case '2' -> status2xx;
            case '3' -> status3xx;
            case '4' -> status4xx;
            case '5' -> status5xx;
            default -> text;
        };
    }
}
