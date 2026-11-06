package com.precificacao.precificacao.cardapio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Cardápio de uma loja já "traduzido" do formato do iFood para o que a tela e o monitor usam. */
public final class CardapioModelos {

    private CardapioModelos() {
    }

    public static final String ITEM = "ITEM";
    public static final String COMPLEMENTO = "COMPLEMENTO";
    public static final String DISPONIVEL = "AVAILABLE";
    public static final String PAUSADO = "UNAVAILABLE";

    public record Cardapio(String merchantId, OffsetDateTime lidoEm, List<Catalogo> catalogos) {

        public int totalItens() {
            return catalogos.stream().mapToInt(c -> c.categorias().stream().mapToInt(k -> k.itens().size()).sum()).sum();
        }

        /** Itens e complementos que não estão vendendo (pausados, em categoria pausada ou não vendáveis). */
        public int totalPausados() {
            int n = 0;
            for (Catalogo c : catalogos) {
                for (Categoria k : c.categorias()) {
                    for (Item i : k.itens()) {
                        if (k.pausada() || i.pausado() || i.naoVendavel() != null) {
                            n++;
                        }
                        for (Grupo g : i.grupos()) {
                            n += (int) g.opcoes().stream().filter(Opcao::pausada).count();
                        }
                    }
                }
            }
            return n;
        }
    }

    public record Catalogo(String id, String contexto, String status, List<Categoria> categorias) {
    }

    public record Categoria(String id, String nome, String status, int ordem, List<Item> itens) {
        public boolean pausada() {
            return PAUSADO.equals(status);
        }
    }

    public record Item(String id, String productId, String nome, String descricao, String imagem, String codigoExterno,
                       String status, BigDecimal preco, BigDecimal precoOriginal, String naoVendavel, int ordem,
                       List<Grupo> grupos) {
        public boolean pausado() {
            return PAUSADO.equals(status);
        }
    }

    public record Grupo(String id, String nome, String status, Integer min, Integer max, List<Opcao> opcoes) {
    }

    public record Opcao(String id, String productId, String nome, String status, BigDecimal preco,
                        BigDecimal precoOriginal, String codigoExterno) {
        public boolean pausada() {
            return PAUSADO.equals(status);
        }
    }

    /** Uma linha de "situação" (item ou complemento) usada pelo monitor de itens. */
    public record Linha(String merchantId, String catalogId, String contexto, String tipo, String entidadeId,
                        String nome, String categoria, String status, String statusCategoria, String naoVendavel,
                        BigDecimal preco) {

        public String chave() {
            return catalogId + "|" + tipo + "|" + entidadeId;
        }

        /** PAUSADO, CATEGORIA, NAO_VENDAVEL ou null. */
        public String problema(boolean considerarNaoVendavel) {
            if (PAUSADO.equals(statusCategoria)) {
                return "CATEGORIA";
            }
            if (PAUSADO.equals(status)) {
                return "PAUSADO";
            }
            if (considerarNaoVendavel && naoVendavel != null && !naoVendavel.isBlank()) {
                return "NAO_VENDAVEL";
            }
            return null;
        }
    }

    /** Achata o cardápio em linhas (itens e complementos; cada complemento conta uma vez por catálogo). */
    public static List<Linha> linhas(Cardapio c, boolean complementos) {
        return linhas(c, complementos, java.util.Set.of());
    }

    public static List<Linha> linhas(Cardapio c, boolean complementos, java.util.Set<String> categoriasIgnoradas) {
        List<Linha> out = new ArrayList<>();
        for (Catalogo cat : c.catalogos()) {
            java.util.Set<String> opcoesVistas = new java.util.HashSet<>();
            for (Categoria k : cat.categorias()) {
                if (categoriasIgnoradas.contains(k.nome())) {
                    continue;
                }
                for (Item i : k.itens()) {
                    out.add(new Linha(c.merchantId(), cat.id(), cat.contexto(), ITEM, i.id(), i.nome(), k.nome(),
                            i.status(), k.status(), i.naoVendavel(), i.preco()));
                    if (!complementos) {
                        continue;
                    }
                    for (Grupo g : i.grupos()) {
                        for (Opcao o : g.opcoes()) {
                            if (opcoesVistas.add(o.id())) {
                                out.add(new Linha(c.merchantId(), cat.id(), cat.contexto(), COMPLEMENTO, o.id(), o.nome(),
                                        g.nome(), o.status(), null, null, o.preco()));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }
}
