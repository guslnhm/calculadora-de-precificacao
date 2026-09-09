package com.precificacao.precificacao.service;

import com.precificacao.precificacao.dto.ImportacaoItensResponseDTO;
import com.precificacao.precificacao.dto.ItemRequestDTO;
import com.precificacao.precificacao.dto.ItemResponseDTO;
import com.precificacao.precificacao.dto.SalvarPrecoVendaRequestDTO;
import com.precificacao.precificacao.entity.Item;
import com.precificacao.precificacao.entity.Loja;
import com.precificacao.precificacao.repository.ItemRepository;
import com.precificacao.precificacao.repository.LojaRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.precificacao.precificacao.enums.Plataforma;
import com.precificacao.precificacao.entity.LojaConfiguracaoPlataforma;
import com.precificacao.precificacao.enums.Plataforma;
import com.precificacao.precificacao.repository.LojaConfiguracaoPlataformaRepository;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.math.RoundingMode;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final LojaRepository lojaRepository;
    private final LojaConfiguracaoPlataformaRepository configuracaoRepository;

    public ItemService(ItemRepository itemRepository, LojaRepository lojaRepository, LojaConfiguracaoPlataformaRepository configuracaoRepository) {
        this.itemRepository = itemRepository;
        this.lojaRepository = lojaRepository;
        this.configuracaoRepository = configuracaoRepository;
    }

    public List<ItemResponseDTO> listarTodos() {
        List<Item> itens = itemRepository.findByAtivoTrue();

        Map<Long, BigDecimal> lucros99PorLoja =
                carregarLucros99PorLoja();

        return itens
                .stream()
                .map(item ->
                        toResponseDTO(
                                item,
                                lucros99PorLoja.get(
                                        item.getLoja().getId()
                                )
                        )
                )
                .toList();
    }

    public List<ItemResponseDTO> listarPorLoja(Long lojaId) {
        List<Item> itens =
                itemRepository.findByLojaIdAndAtivoTrue(lojaId);

        BigDecimal lucro99 =
                configuracaoRepository
                        .findByLojaIdAndPlataforma(
                                lojaId,
                                Plataforma.FOOD99
                        )
                        .filter(config ->
                                Boolean.TRUE.equals(config.getAtivo())
                        )
                        .map(
                                LojaConfiguracaoPlataforma::getPercentualLucro
                        )
                        .orElse(null);

        return itens
                .stream()
                .map(item ->
                        toResponseDTO(item, lucro99)
                )
                .toList();
    }

    public ItemResponseDTO criar(ItemRequestDTO dto) {
        Loja loja = lojaRepository.findById(dto.getLojaId())
                .orElseThrow(() -> new RuntimeException("Loja não encontrada"));

        Item item = new Item();
        item.setLoja(loja);
        item.setNomeItem(dto.getNomeItem());
        item.setCmv(dto.getCmv());
        item.setPrecoVendaInicial(dto.getPrecoVendaInicial());
        item.setRendimento(dto.getRendimento());
        item.setObservacao(dto.getObservacao());
        item.setAtivo(true);
        item.setCriadoEm(LocalDateTime.now());
        item.setAtualizadoEm(LocalDateTime.now());

        Item itemSalvo = itemRepository.save(item);
        return toResponseDTO(itemSalvo);
    }

    public ItemResponseDTO salvarPrecoVendaAtual(Long itemId, SalvarPrecoVendaRequestDTO dto) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item não encontrado"));

        item.setPrecoVendaAtual(dto.getPrecoVendaAtual());
        item.setDataPrecificacao(LocalDateTime.now());
        item.setAtualizadoEm(LocalDateTime.now());

        Item itemSalvo = itemRepository.save(item);
        return toResponseDTO(itemSalvo);
    }

    public ImportacaoItensResponseDTO importarPlanilha(Long lojaId, MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RuntimeException("Arquivo não enviado");
        }

        String nomeArquivo = arquivo.getOriginalFilename();
        if (nomeArquivo == null || !nomeArquivo.toLowerCase().endsWith(".xlsx")) {
            throw new RuntimeException("Envie um arquivo .xlsx válido");
        }

        Loja loja = lojaRepository.findById(lojaId)
                .orElseThrow(() -> new RuntimeException("Loja não encontrada"));

        int totalLinhasLidas = 0;
        int itensImportados = 0;
        int linhasIgnoradas = 0;

        try (InputStream inputStream = arquivo.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            if (sheet.getPhysicalNumberOfRows() <= 1) {
                throw new RuntimeException("A planilha não possui dados para importação");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                totalLinhasLidas++;

                String nomeItem = obterTextoCelula(row.getCell(0)).trim();
                BigDecimal cmv = obterBigDecimalCelula(row.getCell(1));

                boolean linhaVazia = nomeItem.isBlank() && cmv == null;
                if (linhaVazia) {
                    linhasIgnoradas++;
                    continue;
                }

                if (nomeItem.isBlank() || cmv == null || cmv.compareTo(BigDecimal.ZERO) < 0) {
                    linhasIgnoradas++;
                    continue;
                }

                Item item = new Item();
                item.setLoja(loja);
                item.setNomeItem(nomeItem);
                item.setCmv(cmv);
                item.setPrecoVendaInicial(null);
                item.setRendimento(null);
                item.setObservacao(null);
                item.setPrecoVendaAtual(null);
                item.setDataPrecificacao(null);
                item.setAtivo(true);
                item.setCriadoEm(LocalDateTime.now());
                item.setAtualizadoEm(LocalDateTime.now());

                itemRepository.save(item);
                itensImportados++;
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar a planilha");
        }

        ImportacaoItensResponseDTO response = new ImportacaoItensResponseDTO();
        response.setTotalLinhasLidas(totalLinhasLidas);
        response.setItensImportados(itensImportados);
        response.setLinhasIgnoradas(linhasIgnoradas);
        response.setMensagem("Importação concluída com sucesso");
        return response;
    }

    private String obterTextoCelula(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                    } catch (Exception ex) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    private BigDecimal obterBigDecimalCelula(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    String valor = cell.getStringCellValue();
                    if (valor == null || valor.trim().isBlank()) {
                        yield null;
                    }
                    String normalizado = valor.trim().replace(",", ".");
                    yield new BigDecimal(normalizado);
                }
                case FORMULA -> BigDecimal.valueOf(cell.getNumericCellValue());
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal calcularCmvPercentual(
        BigDecimal cmv,
        BigDecimal precoVenda
    ) {
        if (
                cmv == null ||
                precoVenda == null ||
                precoVenda.compareTo(BigDecimal.ZERO) <= 0
        ) {
            return null;
        }

        return cmv
                .multiply(BigDecimal.valueOf(100))
                .divide(precoVenda, 2, RoundingMode.HALF_UP);
    }

    public ItemResponseDTO salvarPrecoVendaPorPlataforma(
        Long itemId,
        Plataforma plataforma,
        SalvarPrecoVendaRequestDTO dto
    ) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() ->
                        new RuntimeException("Item não encontrado")
                );

        switch (plataforma) {
            case IFOOD ->
                    item.setPrecoVendaAtualIfood(
                            dto.getPrecoVendaAtual()
                    );

            case FOOD99 ->
                    item.setPrecoVendaAtual99Food(
                            dto.getPrecoVendaAtual()
                    );

            default ->
                    throw new RuntimeException(
                            "Plataforma não suportada para preço de venda"
                    );
        }

        item.setDataPrecificacao(LocalDateTime.now());
        item.setAtualizadoEm(LocalDateTime.now());

        Item itemSalvo = itemRepository.save(item);

        return toResponseDTO(itemSalvo);
    }

    private ItemResponseDTO toResponseDTO(Item item, BigDecimal lucro99Food) {
        ItemResponseDTO dto = new ItemResponseDTO();
        dto.setId(item.getId());
        dto.setLojaId(item.getLoja().getId());
        dto.setNomeLoja(item.getLoja().getNome());
        dto.setNomeItem(item.getNomeItem());
        dto.setCmv(item.getCmv());
        dto.setPrecoVendaInicial(item.getPrecoVendaInicial());
        dto.setRendimento(item.getRendimento());
        dto.setObservacao(item.getObservacao());
        dto.setPrecoVendaAtual(item.getPrecoVendaAtual());
        dto.setPrecoVendaAtualIfood(item.getPrecoVendaAtualIfood());
        dto.setPrecoVendaAtual99Food(item.getPrecoVendaAtual99Food());

        dto.setCmvPercentualIfood(
                calcularCmvPercentual(
                        item.getCmv(),
                        item.getPrecoVendaAtualIfood()
                )
        );

        dto.setCmvPercentual99Food(
                calcularCmvPercentual(
                        item.getCmv(),
                        item.getPrecoVendaAtual99Food()
                )
        );

        dto.setLucratividadeIfood(
            item.getLoja().getPercentualLucro()
        );

        dto.setLucratividade99Food(
            lucro99Food
        );

        dto.setDataPrecificacao(item.getDataPrecificacao());
        dto.setAtivo(item.getAtivo());
        dto.setCriadoEm(item.getCriadoEm());
        dto.setAtualizadoEm(item.getAtualizadoEm());
        return dto;
    }

    private ItemResponseDTO toResponseDTO(Item item) {
        BigDecimal lucro99 =
            configuracaoRepository
                    .findByLojaIdAndPlataforma(
                            item.getLoja().getId(),
                            Plataforma.FOOD99
                    )
                    .filter(config ->
                            Boolean.TRUE.equals(config.getAtivo())
                    )
                    .map(
                            LojaConfiguracaoPlataforma::getPercentualLucro
                    )
                    .orElse(null);

        return toResponseDTO(item, lucro99);
    }

    public ItemResponseDTO atualizar(Long itemId, ItemRequestDTO dto) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item não encontrado"));

        Loja loja = lojaRepository.findById(dto.getLojaId())
                .orElseThrow(() -> new RuntimeException("Loja não encontrada"));

        item.setLoja(loja);
        item.setNomeItem(dto.getNomeItem());
        item.setCmv(dto.getCmv());
        item.setPrecoVendaInicial(dto.getPrecoVendaInicial());
        item.setRendimento(dto.getRendimento());
        item.setObservacao(dto.getObservacao());
        item.setAtualizadoEm(LocalDateTime.now());

        Item itemSalvo = itemRepository.save(item);
        return toResponseDTO(itemSalvo);
    }

    public void desativar(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item não encontrado"));

        item.setAtivo(false);
        item.setAtualizadoEm(LocalDateTime.now());

        itemRepository.save(item);
    }

    private Map<Long, BigDecimal> carregarLucros99PorLoja() {
        return configuracaoRepository
                .findByPlataforma(Plataforma.FOOD99)
                .stream()
                .filter(config ->
                        Boolean.TRUE.equals(config.getAtivo())
                )
                .collect(
                        Collectors.toMap(
                                config -> config.getLoja().getId(),
                                LojaConfiguracaoPlataforma::getPercentualLucro,
                                (valor1, valor2) -> valor2
                        )
                );
    }
}