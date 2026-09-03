package br.com.outorga.domain.identity;

import br.com.outorga.domain.rights.TipoDeDispositivo;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.time.Instant;
import java.util.UUID;

/**
 * Aparelho registrado numa conta. O identificador vem do cliente e e tratado
 * como opaco: o servidor não tenta adivinhar o que ele significa.
 */
public class Dispositivo {

    private final UUID id;
    private final UUID usuarioId;
    private final String identificador;
    private final TipoDeDispositivo tipo;
    private String apelido;
    private final Instant registradoEm;
    private Instant ultimoUso;

    private Dispositivo(UUID id, UUID usuarioId, String identificador, TipoDeDispositivo tipo,
                        String apelido, Instant registradoEm) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.identificador = identificador;
        this.tipo = tipo;
        this.apelido = apelido;
        this.registradoEm = registradoEm;
        this.ultimoUso = registradoEm;
    }

    public static Result<Dispositivo> registrar(UUID usuarioId, String identificador,
                                                TipoDeDispositivo tipo, String apelido, Instant agora) {
        if (usuarioId == null) {
            return Result.erro(new FalhaDeNegocio("DISPOSITIVO_SEM_CONTA",
                    "Dispositivo precisa de uma conta"));
        }
        if (identificador == null || identificador.isBlank()) {
            return Result.erro(new FalhaDeNegocio("DISPOSITIVO_SEM_ID",
                    "O cliente precisa enviar o identificador do aparelho"));
        }
        if (tipo == null) {
            return Result.erro(new FalhaDeNegocio("DISPOSITIVO_SEM_TIPO",
                    "Informe o tipo do aparelho"));
        }
        var nome = apelido == null || apelido.isBlank() ? tipo.name() : apelido.trim();
        return Result.ok(new Dispositivo(UUID.randomUUID(), usuarioId, identificador.trim(), tipo,
                nome, agora));
    }

    public void marcarUso(Instant agora) {
        this.ultimoUso = agora;
    }

    public UUID id() { return id; }

    public UUID usuarioId() { return usuarioId; }

    public String identificador() { return identificador; }

    public TipoDeDispositivo tipo() { return tipo; }

    public String apelido() { return apelido; }

    public Instant registradoEm() { return registradoEm; }

    public Instant ultimoUso() { return ultimoUso; }

    public static Dispositivo reconstituir(UUID id, UUID usuarioId, String identificador,
                                           TipoDeDispositivo tipo, String apelido,
                                           Instant registradoEm, Instant ultimoUso) {
        var dispositivo = new Dispositivo(id, usuarioId, identificador, tipo, apelido, registradoEm);
        dispositivo.ultimoUso = ultimoUso;
        return dispositivo;
    }
}
