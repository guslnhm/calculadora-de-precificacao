const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

function getToken() {
  return localStorage.getItem("token");
}

function getHeaders(extra = {}) {
  const token = getToken();

  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: token } : {}),
    ...extra,
  };
}

export async function login(dados) {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(dados),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.mensagem || "Erro ao fazer login");
  }

  return body;
}

export async function listarLojas() {
  const response = await fetch(`${API_BASE_URL}/lojas`, {
    headers: getHeaders(),
  });
  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao listar lojas");
  return body;
}

export async function criarLoja(dados) {
  const response = await fetch(`${API_BASE_URL}/lojas`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify(dados),
  });

  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao criar loja");
  return body;
}

export async function listarItens(lojaId) {
  const url = lojaId
    ? `${API_BASE_URL}/itens?lojaId=${lojaId}`
    : `${API_BASE_URL}/itens`;

  const response = await fetch(url, {
    headers: getHeaders(),
  });

  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao listar itens");
  return body;
}

export async function criarItem(dados) {
  const response = await fetch(`${API_BASE_URL}/itens`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify(dados),
  });

  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao criar item");
  return body;
}

export async function simularPrecificacao(dados) {
  const response = await fetch(`${API_BASE_URL}/precificacao/simular`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify(dados),
  });

  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao simular precificação");
  return body;
}

export async function salvarPrecoVendaItem(itemId, dados) {
  const response = await fetch(`${API_BASE_URL}/itens/${itemId}/preco-venda`, {
    method: "PUT",
    headers: getHeaders(),
    body: JSON.stringify(dados),
  });

  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao salvar preço de venda");
  return body;
}

export async function importarItensPlanilha(lojaId, arquivo) {
  const formData = new FormData();
  formData.append("lojaId", lojaId);
  formData.append("arquivo", arquivo);

  const token = getToken();

  const response = await fetch(`${API_BASE_URL}/itens/importar`, {
    method: "POST",
    headers: {
      ...(token ? { Authorization: token } : {}),
    },
    body: formData,
  });

  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao importar planilha");
  return body;
}

export async function buscarPercentuaisLoja(lojaId) {
  const response = await fetch(`${API_BASE_URL}/lojas/${lojaId}/percentuais`, {
    headers: getHeaders(),
  });

  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao buscar percentuais da loja");
  return body;
}

export async function salvarPercentuaisLoja(lojaId, dados) {
  const response = await fetch(`${API_BASE_URL}/lojas/${lojaId}/percentuais`, {
    method: "PUT",
    headers: getHeaders(),
    body: JSON.stringify(dados),
  });

  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao salvar percentuais da loja");
  return body;
}

export async function editarItem(itemId, dados) {
  const response = await fetch(`${API_BASE_URL}/itens/${itemId}`, {
    method: "PUT",
    headers: getHeaders(),
    body: JSON.stringify(dados),
  });

  const body = await response.json();
  if (!response.ok) throw new Error(body.mensagem || "Erro ao editar item");
  return body;
}

export async function desativarItem(itemId) {
  const response = await fetch(`${API_BASE_URL}/itens/${itemId}`, {
    method: "DELETE",
    headers: getHeaders(),
  });

  if (!response.ok) {
    let body = {};
    try {
      body = await response.json();
    } catch {
      body = {};
    }

    throw new Error(body.mensagem || "Erro ao desativar item");
  }
}

export async function simularPrecificacaoReversa(dados) {
  const response = await fetch(`${API_BASE_URL}/precificacao/reversa`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify(dados),
  });

  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.mensagem || "Erro ao simular precificação reversa");
  }

  return body;
}