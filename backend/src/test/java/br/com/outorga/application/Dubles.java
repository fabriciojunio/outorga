package br.com.outorga.application;

import br.com.outorga.application.ports.CifradorDeSenha;
import br.com.outorga.application.ports.EmissorDeToken;
import br.com.outorga.application.ports.EntregaDeVideo;
import br.com.outorga.application.ports.GatewayDePagamento;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.audit.AcaoAuditavel;
import br.com.outorga.domain.audit.RegistroDeAuditoria;
import br.com.outorga.domain.billing.Assinatura;
import br.com.outorga.domain.billing.Cupom;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.catalog.StatusDePublicacao;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.identity.Dispositivo;
import br.com.outorga.domain.identity.Email;
import br.com.outorga.domain.identity.Papel;
import br.com.outorga.domain.identity.Perfil;
import br.com.outorga.domain.identity.Usuario;
import br.com.outorga.domain.live.CanalAoVivo;
import br.com.outorga.domain.live.ProgramaEpg;
import br.com.outorga.domain.playback.SessaoDeReproducao;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.domain.tenant.Tenant;
import br.com.outorga.shared.Result;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dubles em memória para testar caso de uso sem banco.
 *
 * São implementacoes de verdade, não mocks com expectativa: guardam estado e
 * respondem consulta. O teste fica lendo como o sistema se comporta, e não
 * como as chamadas foram feitas, que e o que envelhece mal quando a
 * implementacao muda.
 */
public final class Dubles {

    private Dubles() {}

    public static class Tenants implements Repositorios.DeTenant {
        public final Map<UUID, Tenant> dados = new LinkedHashMap<>();

        @Override
        public Optional<Tenant> porId(UUID id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public Optional<Tenant> porSlug(String slug) {
            return dados.values().stream().filter(t -> t.slug().equals(slug)).findFirst();
        }

        @Override
        public Optional<Tenant> porDominio(String dominio) {
            return dados.values().stream()
                    .filter(t -> dominio.equals(t.dominioProprio())).findFirst();
        }

        @Override
        public List<Tenant> todos() {
            return List.copyOf(dados.values());
        }

        @Override
        public Tenant salvar(Tenant tenant) {
            dados.put(tenant.id(), tenant);
            return tenant;
        }
    }

    public static class Usuarios implements Repositorios.DeUsuario {
        public final Map<UUID, Usuario> dados = new LinkedHashMap<>();

        @Override
        public Optional<Usuario> porId(UUID tenantId, UUID id) {
            return Optional.ofNullable(dados.get(id))
                    .filter(u -> u.tenantId().equals(tenantId));
        }

        @Override
        public Optional<Usuario> porEmail(UUID tenantId, Email email) {
            return dados.values().stream()
                    .filter(u -> u.tenantId().equals(tenantId) && u.email().equals(email))
                    .findFirst();
        }

        @Override
        public List<Usuario> doTenant(UUID tenantId) {
            return dados.values().stream().filter(u -> u.tenantId().equals(tenantId)).toList();
        }

        @Override
        public boolean existeEmail(UUID tenantId, Email email) {
            return porEmail(tenantId, email).isPresent();
        }

        @Override
        public Usuario salvar(Usuario usuario) {
            dados.put(usuario.id(), usuario);
            return usuario;
        }
    }

    public static class Perfis implements Repositorios.DePerfil {
        public final Map<UUID, Perfil> dados = new LinkedHashMap<>();

        @Override
        public Optional<Perfil> porId(UUID id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public List<Perfil> doUsuario(UUID usuarioId) {
            return dados.values().stream().filter(p -> p.usuarioId().equals(usuarioId)).toList();
        }

        @Override
        public int quantidadeDoUsuario(UUID usuarioId) {
            return doUsuario(usuarioId).size();
        }

        @Override
        public Perfil salvar(Perfil perfil) {
            dados.put(perfil.id(), perfil);
            return perfil;
        }

        @Override
        public void remover(UUID id) {
            dados.remove(id);
        }
    }

    public static class Dispositivos implements Repositorios.DeDispositivo {
        public final Map<UUID, Dispositivo> dados = new LinkedHashMap<>();

        @Override
        public Optional<Dispositivo> porIdentificador(UUID usuarioId, String identificador) {
            return dados.values().stream()
                    .filter(d -> d.usuarioId().equals(usuarioId)
                            && d.identificador().equals(identificador))
                    .findFirst();
        }

        @Override
        public List<Dispositivo> doUsuario(UUID usuarioId) {
            return dados.values().stream().filter(d -> d.usuarioId().equals(usuarioId)).toList();
        }

        @Override
        public Dispositivo salvar(Dispositivo dispositivo) {
            dados.put(dispositivo.id(), dispositivo);
            return dispositivo;
        }

        @Override
        public void remover(UUID id) {
            dados.remove(id);
        }
    }

    public static class Titulos implements Repositorios.DeTitulo {
        public final Map<UUID, Titulo> dados = new LinkedHashMap<>();

        @Override
        public Optional<Titulo> porId(UUID tenantId, UUID id) {
            return Optional.ofNullable(dados.get(id)).filter(t -> t.tenantId().equals(tenantId));
        }

        @Override
        public List<Titulo> publicados(UUID tenantId, int pagina, int tamanho) {
            return dados.values().stream()
                    .filter(t -> t.tenantId().equals(tenantId) && t.noAr())
                    .skip((long) pagina * tamanho)
                    .limit(tamanho)
                    .toList();
        }

        @Override
        public List<Titulo> buscar(UUID tenantId, String termo, int limite) {
            return dados.values().stream()
                    .filter(t -> t.tenantId().equals(tenantId) && t.noAr())
                    .filter(t -> t.nome().toLowerCase().contains(termo.toLowerCase()))
                    .limit(limite)
                    .toList();
        }

        @Override
        public List<Titulo> porLicenca(UUID tenantId, UUID licencaId) {
            return dados.values().stream()
                    .filter(t -> t.tenantId().equals(tenantId) && licencaId.equals(t.licencaId()))
                    .toList();
        }

        @Override
        public List<Titulo> sujeitosARevisaoDeDireitos(UUID tenantId) {
            return dados.values().stream()
                    .filter(t -> t.tenantId().equals(tenantId))
                    .filter(t -> t.status() == StatusDePublicacao.PUBLICADO
                            || t.status() == StatusDePublicacao.BLOQUEADO_POR_DIREITO)
                    .toList();
        }

        @Override
        public Titulo salvar(Titulo titulo) {
            dados.put(titulo.id(), titulo);
            return titulo;
        }
    }

    public static class Licencas implements Repositorios.DeLicenca {
        public final Map<UUID, Licenca> dados = new LinkedHashMap<>();

        @Override
        public Optional<Licenca> porId(UUID tenantId, UUID id) {
            return Optional.ofNullable(dados.get(id)).filter(l -> l.tenantId().equals(tenantId));
        }

        @Override
        public List<Licenca> doTenant(UUID tenantId) {
            return dados.values().stream().filter(l -> l.tenantId().equals(tenantId)).toList();
        }

        @Override
        public List<Licenca> vencendoAte(Instant limite) {
            return dados.values().stream()
                    .filter(l -> l.janela().fim() != null && !l.janela().fim().isAfter(limite))
                    .toList();
        }

        @Override
        public Licenca salvar(Licenca licenca) {
            dados.put(licenca.id(), licenca);
            return licenca;
        }
    }

    public static class Planos implements Repositorios.DePlano {
        public final Map<UUID, Plano> dados = new LinkedHashMap<>();

        @Override
        public Optional<Plano> porId(UUID tenantId, UUID id) {
            return Optional.ofNullable(dados.get(id)).filter(p -> p.tenantId().equals(tenantId));
        }

        @Override
        public List<Plano> ativosDoTenant(UUID tenantId) {
            return dados.values().stream()
                    .filter(p -> p.tenantId().equals(tenantId) && p.ativo())
                    .sorted(Comparator.comparingLong(p -> p.preco().centavos()))
                    .toList();
        }

        @Override
        public Plano salvar(Plano plano) {
            dados.put(plano.id(), plano);
            return plano;
        }
    }

    public static class Assinaturas implements Repositorios.DeAssinatura {
        public final Map<UUID, Assinatura> dados = new LinkedHashMap<>();

        @Override
        public Optional<Assinatura> porId(UUID tenantId, UUID id) {
            return Optional.ofNullable(dados.get(id)).filter(a -> a.tenantId().equals(tenantId));
        }

        @Override
        public Optional<Assinatura> vigenteDoUsuario(UUID tenantId, UUID usuarioId) {
            return dados.values().stream()
                    .filter(a -> a.tenantId().equals(tenantId) && a.usuarioId().equals(usuarioId))
                    .max(Comparator.comparing(Assinatura::iniciadaEm));
        }

        @Override
        public Optional<Assinatura> porReferenciaNoGateway(String referencia) {
            return dados.values().stream()
                    .filter(a -> referencia.equals(a.referenciaNoGateway()))
                    .findFirst();
        }

        @Override
        public List<Assinatura> vencendoAte(Instant limite) {
            return List.copyOf(dados.values());
        }

        @Override
        public Assinatura salvar(Assinatura assinatura) {
            dados.put(assinatura.id(), assinatura);
            return assinatura;
        }
    }

    public static class Cupons implements Repositorios.DeCupom {
        public final Map<String, Cupom> dados = new LinkedHashMap<>();

        @Override
        public Optional<Cupom> porCodigo(UUID tenantId, String codigo) {
            return Optional.ofNullable(dados.get(codigo.toUpperCase()));
        }

        @Override
        public Cupom salvar(Cupom cupom) {
            dados.put(cupom.codigo(), cupom);
            return cupom;
        }
    }

    public static class Canais implements Repositorios.DeCanal {
        public final Map<UUID, CanalAoVivo> dados = new LinkedHashMap<>();

        @Override
        public Optional<CanalAoVivo> porId(UUID tenantId, UUID id) {
            return Optional.ofNullable(dados.get(id)).filter(c -> c.tenantId().equals(tenantId));
        }

        @Override
        public List<CanalAoVivo> noAr(UUID tenantId) {
            return dados.values().stream()
                    .filter(c -> c.tenantId().equals(tenantId) && c.noAr()).toList();
        }

        @Override
        public List<CanalAoVivo> doTenant(UUID tenantId) {
            return dados.values().stream().filter(c -> c.tenantId().equals(tenantId)).toList();
        }

        @Override
        public List<CanalAoVivo> porLicenca(UUID tenantId, UUID licencaId) {
            return dados.values().stream()
                    .filter(c -> c.tenantId().equals(tenantId) && licencaId.equals(c.licencaId()))
                    .toList();
        }

        @Override
        public CanalAoVivo salvar(CanalAoVivo canal) {
            dados.put(canal.id(), canal);
            return canal;
        }
    }

    public static class Epg implements Repositorios.DeEpg {
        public final List<ProgramaEpg> dados = new ArrayList<>();

        @Override
        public List<ProgramaEpg> doCanalEntre(UUID tenantId, UUID canalId, Instant de, Instant ate) {
            return dados.stream()
                    .filter(p -> p.canalId().equals(canalId)
                            && p.fim().isAfter(de) && p.inicio().isBefore(ate))
                    .toList();
        }

        @Override
        public void salvarTodos(List<ProgramaEpg> programas) {
            dados.addAll(programas);
        }
    }

    public static class Sessoes implements Repositorios.DeSessao {
        public final Map<UUID, SessaoDeReproducao> dados = new LinkedHashMap<>();

        @Override
        public int abertasDoUsuario(UUID tenantId, UUID usuarioId, Instant agora) {
            return (int) dados.values().stream()
                    .filter(s -> s.tenantId().equals(tenantId) && s.usuarioId().equals(usuarioId))
                    .filter(s -> s.viva(agora))
                    .count();
        }

        @Override
        public Optional<SessaoDeReproducao> porId(UUID id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public SessaoDeReproducao salvar(SessaoDeReproducao sessao) {
            dados.put(sessao.id(), sessao);
            return sessao;
        }

        @Override
        public int fecharAbandonadas(Instant limite) {
            int fechadas = 0;
            for (var sessao : dados.values()) {
                if (sessao.fechadaEm() == null && !sessao.ultimoSinal().isAfter(limite)) {
                    sessao.fechar(sessao.ultimoSinal());
                    fechadas++;
                }
            }
            return fechadas;
        }
    }

    public static class Auditorias implements Repositorios.DeAuditoria {
        public final List<RegistroDeAuditoria> dados = new ArrayList<>();

        @Override
        public void registrar(RegistroDeAuditoria registro) {
            dados.add(registro);
        }

        @Override
        public List<RegistroDeAuditoria> doTenant(UUID tenantId, Instant de, Instant ate, int limite) {
            return dados.stream().filter(r -> tenantId.equals(r.tenantId())).limit(limite).toList();
        }

        public boolean registrou(AcaoAuditavel acao) {
            return dados.stream().anyMatch(r -> r.acao() == acao);
        }

        public long quantasVezes(AcaoAuditavel acao) {
            return dados.stream().filter(r -> r.acao() == acao).count();
        }
    }

    /** Cifrador previsivel: o hash e a senha com um prefixo. */
    public static class Cifrador implements CifradorDeSenha {

        @Override
        public String cifrar(String senhaEmTextoClaro) {
            return "hash:" + senhaEmTextoClaro;
        }

        @Override
        public boolean confere(String senhaEmTextoClaro, String hash) {
            return hash != null && hash.equals(cifrar(senhaEmTextoClaro));
        }
    }

    public static class Emissor implements EmissorDeToken {
        public final List<UUID> revogados = new ArrayList<>();

        @Override
        public Par emitir(UUID tenantId, UUID usuarioId, java.util.Set<Papel> papeis) {
            var agora = Instant.parse("2026-08-24T12:00:00Z");
            return new Par("acesso-" + usuarioId, agora.plus(Duration.ofMinutes(15)),
                    "refresh-" + usuarioId, agora.plus(Duration.ofDays(14)));
        }

        @Override
        public Result<Conteudo> validarAcesso(String token) {
            return Result.erro("TOKEN_INVALIDO", "duble nao valida token");
        }

        @Override
        public Result<Par> renovar(String refreshToken) {
            return Result.erro("TOKEN_INVALIDO", "duble nao renova token");
        }

        @Override
        public void revogar(UUID usuarioId) {
            revogados.add(usuarioId);
        }
    }

    public static class Entrega implements EntregaDeVideo {
        public boolean falhar = false;

        @Override
        public Result<EnderecoDeReproducao> assinarVod(String referencia, Qualidade qualidade,
                                                       Duration validade) {
            if (falhar) {
                return Result.erro("VIDEO_INDISPONIVEL", "duble configurado para falhar");
            }
            return Result.ok(new EnderecoDeReproducao(
                    "https://cdn.exemplo/" + referencia + "/" + qualidade.alturaMaxima() + "p.m3u8",
                    "HLS", Instant.parse("2026-08-24T12:05:00Z"), null));
        }

        @Override
        public Result<EnderecoDeReproducao> assinarAoVivo(String referencia, Duration validade) {
            return assinarVod(referencia, Qualidade.HD, validade);
        }
    }

    public static class Gateway implements GatewayDePagamento {
        public final List<String> cancelados = new ArrayList<>();
        public boolean autentico = true;
        public EventoDeCobranca proximoEvento;
        public boolean falharAoAbrir = false;

        @Override
        public Result<Cobranca> abrirAssinatura(PedidoDeAssinatura pedido) {
            if (falharAoAbrir) {
                return Result.erro("GATEWAY_INDISPONIVEL", "duble configurado para falhar");
            }
            return Result.ok(new Cobranca("ref-" + pedido.assinaturaId(),
                    "https://checkout.exemplo/" + pedido.assinaturaId(), "pix-copia-e-cola"));
        }

        @Override
        public Result<Void> cancelarAssinatura(String referenciaNoGateway) {
            cancelados.add(referenciaNoGateway);
            return Result.ok(null);
        }

        @Override
        public boolean webhookAutentico(Map<String, String> cabecalhos, String corpo) {
            return autentico;
        }

        @Override
        public Result<EventoDeCobranca> interpretar(String corpo) {
            return proximoEvento == null
                    ? Result.erro("WEBHOOK_ILEGIVEL", "duble sem evento configurado")
                    : Result.ok(proximoEvento);
        }

        public static EventoDeCobranca confirmado(String referencia, long centavos) {
            return new EventoDeCobranca(EventoDeCobranca.Tipo.CONFIRMADO, referencia,
                    Dinheiro.reais(centavos), "teste");
        }

        public static EventoDeCobranca recusado(String referencia, String motivo) {
            return new EventoDeCobranca(EventoDeCobranca.Tipo.RECUSADO, referencia, null, motivo);
        }
    }
}
