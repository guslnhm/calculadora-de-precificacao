package com.precificacao.precificacao.cardapio;

import com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio;
import com.precificacao.precificacao.cardapio.CardapioModelos.Catalogo;
import com.precificacao.precificacao.cardapio.CardapioModelos.Categoria;
import com.precificacao.precificacao.cardapio.CardapioModelos.Grupo;
import com.precificacao.precificacao.cardapio.CardapioModelos.Item;
import com.precificacao.precificacao.cardapio.CardapioModelos.Opcao;
import com.precificacao.precificacao.cardapio.CardapioRepository.Alteracao;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.ifood.catalog.IfoodCatalogClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cardápio na tela: leitura (salva ou ao vivo) e alterações de preço/status direto no iFood,
 * cada uma registrada com quem fez e com opção de desfazer.
 */
@Service
public class CardapioService {

    /** Erro de regra (vira 400 com a mensagem na tela). */
    public static class Recusado extends RuntimeException {
        public Recusado(String mensagem) {
            super(mensagem);
        }
    }

    /** O que o iFood precisa receber para alterar item ou complemento (trocável nos testes). */
    public interface Escrita {
        void status(String merchantId, String tipo, String id, String status);

        void preco(String merchantId, String tipo, String id, BigDecimal valor);
    }

    private final CardapioRepository repo;
    private final MonitorItensService monitor;
    private final Escrita escrita;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CardapioService(CardapioRepository repo, MonitorItensService monitor, IfoodCatalogClient catalog, Clock clock) {
        this(repo, monitor, new Escrita() {
            @Override
            public void status(String merchantId, String tipo, String id, String status) {
                if (CardapioModelos.ITEM.equals(tipo)) {
                    catalog.alterarStatusItem(merchantId, id, status);
                } else {
                    catalog.alterarStatusComplemento(merchantId, id, status);
                }
            }

            @Override
            public void preco(String merchantId, String tipo, String id, BigDecimal valor) {
                // sem preço "de/por": o valor original fica igual ao novo
                if (CardapioModelos.ITEM.equals(tipo)) {
                    catalog.alterarPrecoItem(merchantId, id, valor, valor);
                } else {
                    catalog.alterarPrecoComplemento(merchantId, id, valor, valor);
                }
            }
        }, clock);
    }

    public CardapioService(CardapioRepository repo, MonitorItensService monitor, Escrita escrita, Clock clock) {
        this.repo = repo;
        this.monitor = monitor;
        this.escrita = escrita;
        this.clock = clock;
    }

    /** Cardápio salvo; lê do iFood se ainda não houver leitura ou se pedirem para atualizar. */
    public Cardapio cardapio(String merchantId, boolean atualizar) {
        if (!atualizar) {
            Optional<Cardapio> salvo = repo.cardapio(merchantId);
            if (salvo.isPresent()) {
                return salvo.get();
            }
        }
        return monitor.lerLoja(merchantId);
    }

    public Alteracao alterarStatus(String merchantId, String tipo, String id, String status, String usuario) {
        String novo = status == null ? "" : status.strip().toUpperCase();
        if (!novo.equals(CardapioModelos.DISPONIVEL) && !novo.equals(CardapioModelos.PAUSADO)) {
            throw new Recusado("Status inválido: use AVAILABLE (ativo) ou UNAVAILABLE (pausado)");
        }
        return aplicar(merchantId, tipo(tipo), id, "STATUS", novo, usuario, null);
    }

    public Alteracao alterarPreco(String merchantId, String tipo, String id, BigDecimal preco, String usuario) {
        if (preco == null || preco.signum() < 0) {
            throw new Recusado("Informe um preço válido");
        }
        String t = tipo(tipo);
        if (CardapioModelos.ITEM.equals(t) && preco.signum() == 0) {
            throw new Recusado("Item não pode custar R$ 0,00 (complemento pode)");
        }
        return aplicar(merchantId, t, id, "PRECO", preco.setScale(2, RoundingMode.HALF_UP).toPlainString(), usuario, null);
    }

    /** Volta o valor de antes de uma alteração. */
    public Alteracao desfazer(long alteracaoId, String usuario) {
        Alteracao a = repo.alteracao(alteracaoId).orElseThrow(() -> new Recusado("Alteração não encontrada"));
        if (!"FEITA".equals(a.situacao())) {
            throw new Recusado("Esta alteração não pode ser desfeita (situação: " + a.situacao() + ")");
        }
        if (a.antes() == null) {
            throw new Recusado("Não sei o valor de antes desta alteração");
        }
        Alteracao volta = aplicar(a.merchantId(), a.tipo(), a.entidadeId(), a.campo(), a.antes(), usuario, a.id());
        if ("FEITA".equals(volta.situacao())) {
            repo.marcarDesfeita(a.id(), usuario, OffsetDateTime.now(clock));
        }
        return volta;
    }

    /** Mudança de preço de um reajuste em massa (desfazId preenchido quando é a volta de outra alteração). */
    public record MudancaPreco(String tipo, String id, BigDecimal preco, Long desfazId) {
    }

    /**
     * Várias mudanças de preço numa loja: chama o iFood uma a uma, registra cada alteração no lote
     * e só no fim salva o cardápio e atualiza o monitor (silencioso). depoisDeCada recebe cada alteração registrada.
     */
    public List<Alteracao> alterarPrecosEmLote(String merchantId, List<MudancaPreco> mudancas, String usuario,
                                               Long loteId, java.util.function.Consumer<Alteracao> depoisDeCada) {
        Cardapio c = repo.cardapio(merchantId).orElseGet(() -> monitor.lerLoja(merchantId));
        List<Alteracao> feitas = new ArrayList<>();
        boolean mudouAlgo = false;
        for (MudancaPreco m : mudancas) {
            String tipo = tipo(m.tipo());
            OffsetDateTime agora = OffsetDateTime.now(clock);
            String novo = m.preco().setScale(2, RoundingMode.HALF_UP).toPlainString();
            Optional<Encontrado> e = encontrar(c, tipo, m.id());
            String nome = e.map(Encontrado::nome).orElse(m.id());
            String antes = e.map(Encontrado::preco).map(BigDecimal::toPlainString).orElse(null);
            String situacao = "FEITA";
            String erro = null;
            if (e.isEmpty()) {
                situacao = "FALHOU";
                erro = "Não está mais no cardápio desta loja";
            } else {
                try {
                    escrita.preco(merchantId, tipo, m.id(), new BigDecimal(novo));
                    c = alterar(c, tipo, m.id(), "PRECO", novo);
                    mudouAlgo = true;
                } catch (RuntimeException ex) {
                    situacao = "FALHOU";
                    erro = ex.getMessage();
                }
            }
            long aid = repo.registrarAlteracao(merchantId, tipo, m.id(), nome, "PRECO", antes, novo, usuario, agora,
                    situacao, erro, m.desfazId(), loteId);
            if ("FEITA".equals(situacao) && m.desfazId() != null) {
                repo.marcarDesfeita(m.desfazId(), usuario, agora);
            }
            Alteracao a = repo.alteracao(aid).orElseThrow();
            feitas.add(a);
            if (depoisDeCada != null) {
                depoisDeCada.accept(a);
            }
        }
        if (mudouAlgo) {
            repo.salvarCardapio(c);
            monitor.registrar(merchantId, c, true);
        }
        return feitas;
    }

    public List<Alteracao> alteracoes(String merchantId) {
        return repo.alteracoes(merchantId, 200);
    }

    // ------------------------------------------------------------------ apoio

    private Alteracao aplicar(String merchantId, String tipo, String id, String campo, String novo, String usuario,
                              Long desfazId) {
        Cardapio c = repo.cardapio(merchantId).orElseGet(() -> monitor.lerLoja(merchantId));
        Encontrado e = encontrar(c, tipo, id)
                .orElseThrow(() -> new Recusado("Não achei este " + (CardapioModelos.ITEM.equals(tipo) ? "item" : "complemento")
                        + " no cardápio salvo. Clique em Atualizar e tente de novo."));
        String antes = "STATUS".equals(campo) ? e.status() : e.preco() == null ? null : e.preco().toPlainString();
        OffsetDateTime agora = OffsetDateTime.now(clock);
        try {
            if ("STATUS".equals(campo)) {
                escrita.status(merchantId, tipo, id, novo);
            } else {
                escrita.preco(merchantId, tipo, id, new BigDecimal(novo));
            }
        } catch (IfoodException ex) {
            long aid = repo.registrarAlteracao(merchantId, tipo, id, e.nome(), campo, antes, novo, usuario, agora,
                    "FALHOU", ex.getMessage(), desfazId);
            return repo.alteracao(aid).orElseThrow();
        }
        Cardapio novoCardapio = alterar(c, tipo, id, campo, novo);
        repo.salvarCardapio(novoCardapio);
        monitor.registrar(merchantId, novoCardapio, true); // não vira alerta: foi a gente que mudou
        long aid = repo.registrarAlteracao(merchantId, tipo, id, e.nome(), campo, antes, novo, usuario, agora,
                "FEITA", null, desfazId);
        return repo.alteracao(aid).orElseThrow();
    }

    record Encontrado(String nome, String status, BigDecimal preco) {
    }

    static String tipo(String tipo) {
        String t = tipo == null ? "" : tipo.strip().toUpperCase();
        if (t.equals("ITEM")) {
            return CardapioModelos.ITEM;
        }
        if (t.equals("COMPLEMENTO") || t.equals("OPCAO") || t.equals("OPTION")) {
            return CardapioModelos.COMPLEMENTO;
        }
        throw new Recusado("Tipo inválido: use ITEM ou COMPLEMENTO");
    }

    static Optional<Encontrado> encontrar(Cardapio c, String tipo, String id) {
        for (Catalogo cat : c.catalogos()) {
            for (Categoria k : cat.categorias()) {
                for (Item i : k.itens()) {
                    if (CardapioModelos.ITEM.equals(tipo) && i.id().equals(id)) {
                        return Optional.of(new Encontrado(i.nome(), i.status(), i.preco()));
                    }
                    for (Grupo g : i.grupos()) {
                        for (Opcao o : g.opcoes()) {
                            if (CardapioModelos.COMPLEMENTO.equals(tipo) && o.id().equals(id)) {
                                return Optional.of(new Encontrado(o.nome(), o.status(), o.preco()));
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Cópia do cardápio com o status/preço trocado (em todos os lugares onde o item/complemento aparece). */
    static Cardapio alterar(Cardapio c, String tipo, String id, String campo, String novo) {
        boolean status = "STATUS".equals(campo);
        BigDecimal preco = status ? null : new BigDecimal(novo);
        List<Catalogo> cats = new ArrayList<>();
        for (Catalogo cat : c.catalogos()) {
            List<Categoria> ks = new ArrayList<>();
            for (Categoria k : cat.categorias()) {
                List<Item> is = new ArrayList<>();
                for (Item i : k.itens()) {
                    List<Grupo> gs = new ArrayList<>();
                    for (Grupo g : i.grupos()) {
                        List<Opcao> os = new ArrayList<>();
                        for (Opcao o : g.opcoes()) {
                            if (CardapioModelos.COMPLEMENTO.equals(tipo) && o.id().equals(id)) {
                                o = new Opcao(o.id(), o.productId(), o.nome(), status ? novo : o.status(),
                                        status ? o.preco() : preco, status ? o.precoOriginal() : preco, o.codigoExterno());
                            }
                            os.add(o);
                        }
                        gs.add(new Grupo(g.id(), g.nome(), g.status(), g.min(), g.max(), os));
                    }
                    if (CardapioModelos.ITEM.equals(tipo) && i.id().equals(id)) {
                        i = new Item(i.id(), i.productId(), i.nome(), i.descricao(), i.imagem(), i.codigoExterno(),
                                status ? novo : i.status(), status ? i.preco() : preco,
                                status ? i.precoOriginal() : preco, i.naoVendavel(), i.ordem(), gs);
                    } else {
                        i = new Item(i.id(), i.productId(), i.nome(), i.descricao(), i.imagem(), i.codigoExterno(),
                                i.status(), i.preco(), i.precoOriginal(), i.naoVendavel(), i.ordem(), gs);
                    }
                    is.add(i);
                }
                ks.add(new Categoria(k.id(), k.nome(), k.status(), k.ordem(), is));
            }
            cats.add(new Catalogo(cat.id(), cat.contexto(), cat.status(), ks));
        }
        return new Cardapio(c.merchantId(), c.lidoEm(), cats);
    }
}
