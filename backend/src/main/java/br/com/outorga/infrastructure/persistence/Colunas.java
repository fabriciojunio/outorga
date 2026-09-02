package br.com.outorga.infrastructure.persistence;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Conversoes entre o ResultSet e os tipos do dominio.
 *
 * Arranjo e jsonb chegam ao banco como literal com cast explicito no SQL
 * ({@code :papeis::text[]}). O driver aceita, o plano de execucao nao muda e
 * evita espalhar {@code createArrayOf} com Connection na mao por todo
 * repositorio. O preco e ter que escapar o literal, feito uma vez aqui.
 */
public final class Colunas {

    private Colunas() {}

    public static UUID uuid(ResultSet rs, String coluna) throws SQLException {
        var valor = rs.getObject(coluna);
        return valor == null ? null : (UUID) valor;
    }

    public static Instant instante(ResultSet rs, String coluna) throws SQLException {
        var valor = rs.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }

    public static Duration duracao(ResultSet rs, String coluna) throws SQLException {
        long segundos = rs.getLong(coluna);
        return rs.wasNull() ? null : Duration.ofSeconds(segundos);
    }

    public static Long segundos(Duration duracao) {
        return duracao == null ? null : duracao.toSeconds();
    }

    public static Integer inteiroOuNulo(ResultSet rs, String coluna) throws SQLException {
        int valor = rs.getInt(coluna);
        return rs.wasNull() ? null : valor;
    }

    public static Set<String> conjunto(ResultSet rs, String coluna) throws SQLException {
        Array array = rs.getArray(coluna);
        if (array == null) {
            return Set.of();
        }
        Object bruto = array.getArray();
        var saida = new LinkedHashSet<String>();
        for (Object item : (Object[]) bruto) {
            if (item != null) {
                saida.add(item.toString());
            }
        }
        return saida;
    }

    /** Monta o literal de arranjo do PostgreSQL, escapando o que precisa. */
    public static String literalDeArranjo(Collection<?> valores) {
        if (valores == null || valores.isEmpty()) {
            return "{}";
        }
        return valores.stream()
                .map(String::valueOf)
                .map(Colunas::escaparItem)
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String escaparItem(String valor) {
        return "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** JSON simples de mapa de texto, suficiente para detalhes de auditoria. */
    public static String literalDeJson(Map<String, String> mapa) {
        if (mapa == null || mapa.isEmpty()) {
            return "{}";
        }
        return mapa.entrySet().stream()
                .map(e -> aspas(e.getKey()) + ":" + aspas(String.valueOf(e.getValue())))
                .collect(Collectors.joining(",", "{", "}"));
    }

    public static Map<String, String> jsonParaMapa(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Map.of();
        }
        var mapa = new java.util.LinkedHashMap<String, String>();
        var corpo = json.trim();
        corpo = corpo.substring(1, corpo.length() - 1);
        for (String par : dividirNoTopo(corpo)) {
            int dois = posicaoDosDoisPontos(par);
            if (dois < 0) {
                continue;
            }
            mapa.put(desescapar(par.substring(0, dois)), desescapar(par.substring(dois + 1)));
        }
        return mapa;
    }

    private static List<String> dividirNoTopo(String corpo) {
        var partes = new java.util.ArrayList<String>();
        boolean dentroDeAspas = false;
        boolean escapado = false;
        var atual = new StringBuilder();
        for (char c : corpo.toCharArray()) {
            if (escapado) {
                atual.append(c);
                escapado = false;
                continue;
            }
            if (c == '\\') {
                atual.append(c);
                escapado = true;
                continue;
            }
            if (c == '"') {
                dentroDeAspas = !dentroDeAspas;
            }
            if (c == ',' && !dentroDeAspas) {
                partes.add(atual.toString());
                atual.setLength(0);
                continue;
            }
            atual.append(c);
        }
        if (!atual.isEmpty()) {
            partes.add(atual.toString());
        }
        return partes;
    }

    private static int posicaoDosDoisPontos(String par) {
        boolean dentroDeAspas = false;
        boolean escapado = false;
        for (int i = 0; i < par.length(); i++) {
            char c = par.charAt(i);
            if (escapado) {
                escapado = false;
                continue;
            }
            if (c == '\\') {
                escapado = true;
                continue;
            }
            if (c == '"') {
                dentroDeAspas = !dentroDeAspas;
            }
            if (c == ':' && !dentroDeAspas) {
                return i;
            }
        }
        return -1;
    }

    private static String aspas(String valor) {
        return "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String desescapar(String bruto) {
        var texto = bruto.trim();
        if (texto.startsWith("\"") && texto.endsWith("\"") && texto.length() >= 2) {
            texto = texto.substring(1, texto.length() - 1);
        }
        return texto.replace("\\n", "\n").replace("\\r", "\r")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
