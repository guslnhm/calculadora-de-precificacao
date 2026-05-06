package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.dto.ImportacaoItensResponseDTO;
import com.precificacao.precificacao.dto.ItemRequestDTO;
import com.precificacao.precificacao.dto.ItemResponseDTO;
import com.precificacao.precificacao.dto.SalvarPrecoVendaRequestDTO;
import com.precificacao.precificacao.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}