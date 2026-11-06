package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.dto.ImportacaoItensResponseDTO;
import com.precificacao.precificacao.dto.ItemRequestDTO;
import com.precificacao.precificacao.dto.ItemResponseDTO;
import com.precificacao.precificacao.dto.SalvarPrecoVendaRequestDTO;
import com.precificacao.precificacao.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.precificacao.precificacao.enums.Plataforma;

import java.util.List;

@RestController
@RequestMapping("/itens")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<ItemResponseDTO> listarTodos(
            @RequestParam(required = false) Long lojaId
    ) {
        if (lojaId != null) {
            return itemService.listarPorLoja(lojaId);
        }
        return itemService.listarTodos();
    }

    @PostMapping
    public ItemResponseDTO criar(@Valid @RequestBody ItemRequestDTO dto) {
        return itemService.criar(dto);
    }

    @PutMapping("/{id}/preco-venda")
    public ItemResponseDTO salvarPrecoVenda(
            @PathVariable Long id,
            @Valid @RequestBody SalvarPrecoVendaRequestDTO dto
    ) {
        return itemService.salvarPrecoVendaAtual(id, dto);
    }

    @PostMapping("/importar")
    public ImportacaoItensResponseDTO importarPlanilha(
            @RequestParam Long lojaId,
            @RequestParam("arquivo") MultipartFile arquivo
    ) {
        return itemService.importarPlanilha(lojaId, arquivo);
    }

    /** Planilha .xlsx com os itens da loja para o cliente preencher o custo. */
    @GetMapping("/planilha-custos")
    public org.springframework.http.ResponseEntity<byte[]> planilhaCustos(@RequestParam Long lojaId) {
        byte[] xlsx = itemService.planilhaCustos(lojaId);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"custos-loja-" + lojaId + ".xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @PutMapping("/{id}")
    public ItemResponseDTO atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ItemRequestDTO dto
    ) {
        return itemService.atualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public void desativar(@PathVariable Long id) {
        itemService.desativar(id);
    }

    @PutMapping("/{id}/preco-venda/{plataforma}")
    public ItemResponseDTO salvarPrecoVendaPorPlataforma(
            @PathVariable Long id,
            @PathVariable Plataforma plataforma,
            @Valid @RequestBody SalvarPrecoVendaRequestDTO dto
    ) {
        return itemService.salvarPrecoVendaPorPlataforma(
                id,
                plataforma,
                dto
        );
    }
}