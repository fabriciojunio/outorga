package br.com.outorga.application.usecases.catalog;

import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.shared.Falhas;
import br.com.outorga.shared.Result;

import java.util.List;
import java.util.UUID;

/**
 * Catalogo visto pelo espectador. Ja chega filtrado pelo teto de
 * classificacao do perfil: conteudo adulto nao pode aparecer nem como capa
 * borrada num perfil infantil.
 */
public class ListarCatalogo {

    private static final int TAMANHO_MAXIMO = 60;

    private final Repositorios.DeTitulo titulos;
    private final Repositorios.DePerfil perfis;

    public ListarCatalogo(Repositorios.DeTitulo titulos, Repositorios.DePerfil perfis) {
        this.titulos = titulos;
        this.perfis = perfis;
    }

    public Result<List<Titulo>> executar(UUID tenantId, UUID perfilId, int pagina, int tamanho) {
        var teto = ClassificacaoIndicativa.DEZOITO_ANOS;
        if (perfilId != null) {
            var perfil = perfis.porId(perfilId);
            if (perfil.isEmpty()) {
                return Result.erro(Falhas.naoEncontrado("Perfil"));
            }
            teto = perfil.get().tetoDeClassificacao();
        }
        var limite = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO);
        var achados = titulos.publicados(tenantId, Math.max(pagina, 0), limite);
        var tetoFinal = teto;
        return Result.ok(achados.stream().filter(t -> t.visivelPara(tetoFinal)).toList());
    }

    public Result<List<Titulo>> buscar(UUID tenantId, UUID perfilId, String termo, int limite) {
        if (termo == null || termo.trim().length() < 2) {
            return Result.erro(Falhas.invalido("Digite ao menos dois caracteres"));
        }
        var teto = ClassificacaoIndicativa.DEZOITO_ANOS;
        if (perfilId != null) {
            var perfil = perfis.porId(perfilId);
            if (perfil.isEmpty()) {
                return Result.erro(Falhas.naoEncontrado("Perfil"));
            }
            teto = perfil.get().tetoDeClassificacao();
        }
        var tetoFinal = teto;
        var achados = titulos.buscar(tenantId, termo.trim(), Math.min(Math.max(limite, 1), TAMANHO_MAXIMO));
        return Result.ok(achados.stream().filter(t -> t.visivelPara(tetoFinal)).toList());
    }
}
