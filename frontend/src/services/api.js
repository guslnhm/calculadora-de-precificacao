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

export async function listarLojas(plataforma = null) {
  const url = plataforma
    ? `${API_BASE_URL}/lojas?plataforma=${plataforma}`
    : `${API_BASE_URL}/lojas`;

  const response = await fetch(url, {
    headers: getHeaders(),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(
      body.mensagem || "Erro ao listar lojas"
    );
  }

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

export async function salvarPrecoVendaItem(
  itemId,
  plataforma,
  dados
) {
  const response = await fetch(
    `${API_BASE_URL}/itens/${itemId}/preco-venda/${plataforma}`,
    {
      method: "PUT",
      headers: getHeaders(),
      body: JSON.stringify(dados),
    }
  );

  const body = await response.json();

  if (!response.ok) {
    throw new Error(
      body.mensagem || "Erro ao salvar preço de venda"
    );
  }

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

export async function simularPrecificacao99(dados) {
  const response = await fetch(`${API_BASE_URL}/precificacao/99food`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify(dados),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.mensagem || "Erro ao simular precificação 99Food");
  }

  return body;
}

export async function buscarConfiguracaoPlataforma(lojaId, plataforma) {
  const response = await fetch(
    `${API_BASE_URL}/lojas/${lojaId}/configuracoes/${plataforma}`,
    {
      method: "GET",
      headers: getHeaders(),
    }
  );

  const body = await response.json();

  if (!response.ok) {
    throw new Error(
      body.mensagem || "Erro ao buscar parâmetros da loja"
    );
  }

  return body;
}

export async function salvarConfiguracaoPlataforma(lojaId, dados) {
  const response = await fetch(
    `${API_BASE_URL}/lojas/${lojaId}/configuracoes`,
    {
      method: "PUT",
      headers: getHeaders(),
      body: JSON.stringify(dados),
    }
  );

  const body = await response.json();

  if (!response.ok) {
    throw new Error(
      body.mensagem || "Erro ao salvar parâmetros da loja"
    );
  }

  return body;
}

export async function buscarPlataformasLoja(lojaId) {
  const response = await fetch(
    `${API_BASE_URL}/lojas/${lojaId}/plataformas`,
    {
      method: "GET",
      headers: getHeaders(),
    }
  );

  const body = await response.json();

  if (!response.ok) {
    throw new Error(
      body.mensagem || "Erro ao buscar plataformas da loja"
    );
  }

  return body;
}

export async function atualizarPlataformasLoja(lojaId, plataformas) {
  const response = await fetch(
    `${API_BASE_URL}/lojas/${lojaId}/plataformas`,
    {
      method: "PUT",
      headers: getHeaders(),
      body: JSON.stringify({
        plataformas,
      }),
    }
  );

  const body = await response.json();

  if (!response.ok) {
    throw new Error(
      body.mensagem || "Erro ao atualizar plataformas da loja"
    );
  }

  return body;
}

export async function desativarLoja(lojaId) {
  const response = await fetch(
    `${API_BASE_URL}/lojas/${lojaId}`,
    {
      method: "DELETE",
      headers: getHeaders(),
    }
  );

  if (!response.ok) {
    let body = {};

    try {
      body = await response.json();
    } catch {
      body = {};
    }

    throw new Error(
      body.mensagem || "Erro ao desativar loja"
    );
  }
}

// ------

export async function atualizarLoja(lojaId, dados) {
  const response = await fetch(
    `${API_BASE_URL}/lojas/${lojaId}`,
    {
      method: "PUT",
      headers: getHeaders(),
      body: JSON.stringify(dados),
    }
  );

  const body = await response.json();

  if (!response.ok) {
    throw new Error(
      body.mensagem || "Erro ao atualizar loja"
    );
  }

  return body;
}
// ------ Monitor iFood e grupos de WhatsApp

async function requisicao(metodo, caminho, dados) {
  const response = await fetch(`${API_BASE_URL}${caminho}`, {
    method: metodo,
    headers: getHeaders(),
    ...(dados !== undefined ? { body: JSON.stringify(dados) } : {}),
  });

  let body = null;
  const texto = await response.text();
  if (texto) {
    try {
      body = JSON.parse(texto);
    } catch {
      body = texto;
    }
  }

  if (!response.ok) {
    const mensagem =
      (body && (body.mensagem || body.erro || body.message)) ||
      `Erro ${response.status} ao chamar ${caminho}`;
    throw new Error(typeof mensagem === "string" ? mensagem : JSON.stringify(mensagem));
  }

  return body;
}

function query(params) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (Array.isArray(v)) v.forEach((x) => q.append(k, x));
    else if (v !== undefined && v !== null && v !== "") q.append(k, v);
  });
  const s = q.toString();
  return s ? `?${s}` : "";
}

export const monitorApi = {
  painel: () => requisicao("GET", "/monitor/lojas"),
  lojasIfood: () => requisicao("GET", "/monitor/lojas-ifood"),
  incidentes: (de, ate, lojas) => requisicao("GET", `/monitor/incidentes${query({ de, ate, lojas })}`),
  historico: (merchantId, de, ate) =>
    requisicao("GET", `/monitor/historico/${encodeURIComponent(merchantId)}${query({ de, ate })}`),
  mensagens: (limite = 100) => requisicao("GET", `/monitor/mensagens${query({ limite })}`),
  configuracao: () => requisicao("GET", "/monitor/configuracao"),
  rodarCiclo: () => requisicao("POST", "/monitor/ciclo"),
  enviarResumo: (dia) => requisicao("POST", `/monitor/resumo${query({ dia })}`),
};

export const whatsappApi = {
  grupos: () => requisicao("GET", "/whatsapp/grupos"),
  criarGrupo: (dados) => requisicao("POST", "/whatsapp/grupos", dados),
  atualizarGrupo: (id, dados) => requisicao("PUT", `/whatsapp/grupos/${id}`, dados),
  removerGrupo: (id) => requisicao("DELETE", `/whatsapp/grupos/${id}`),
  gruposDisponiveis: () => requisicao("GET", "/whatsapp/grupos-disponiveis"),
  situacao: () => requisicao("GET", "/whatsapp/situacao"),
  conectar: () => requisicao("POST", "/whatsapp/conectar"),
  testarGrupo: (id) => requisicao("POST", `/whatsapp/grupos/${id}/teste`),
};

export const cardapioApi = {
  lojas: () => requisicao("GET", "/cardapio/lojas"),
  pausados: () => requisicao("GET", "/cardapio/pausados"),
  cardapio: (merchantId, atualizar = false) =>
    requisicao("GET", `/cardapio/${encodeURIComponent(merchantId)}${atualizar ? "?atualizar=true" : ""}`),
  alterarStatus: (merchantId, tipo, id, status) =>
    requisicao("POST", `/cardapio/${encodeURIComponent(merchantId)}/status`, { tipo, id, status }),
  alterarPreco: (merchantId, tipo, id, preco) =>
    requisicao("POST", `/cardapio/${encodeURIComponent(merchantId)}/preco`, { tipo, id, preco }),
  alteracoes: (merchantId) => requisicao("GET", `/cardapio/${encodeURIComponent(merchantId)}/alteracoes`),
  desfazer: (id) => requisicao("POST", `/cardapio/alteracoes/${id}/desfazer`),
};

export const reajusteApi = {
  todasLojasIfood: () => requisicao("GET", "/reajuste/lojas-ifood"),
  loja: (lojaId) => requisicao("GET", `/reajuste/${lojaId}`),
  salvarRegras: (lojaId, regras) => requisicao("PUT", `/reajuste/${lojaId}/regras`, regras),
  salvarLojasIfood: (lojaId, merchantIds) => requisicao("PUT", `/reajuste/${lojaId}/lojas-ifood`, { merchantIds }),
  desligarLojaIfood: (lojaId, merchantId) =>
    requisicao("DELETE", `/reajuste/${lojaId}/lojas-ifood/${encodeURIComponent(merchantId)}`),
  vinculos: (lojaId, merchantId) => requisicao("GET", `/reajuste/${lojaId}/vinculos/${encodeURIComponent(merchantId)}`),
  salvarVinculos: (lojaId, merchantId, lista) =>
    requisicao("PUT", `/reajuste/${lojaId}/vinculos/${encodeURIComponent(merchantId)}`, lista),
  aceitarSugestoes: (lojaId, merchantId, minimo) =>
    requisicao("POST", `/reajuste/${lojaId}/vinculos/${encodeURIComponent(merchantId)}/aceitar-sugestoes${query({ minimo })}`),
  previa: (lojaId, fonte) => requisicao("GET", `/reajuste/${lojaId}/previa${query({ fonte })}`),
  publicar: (lojaId, fonte, chaves) => requisicao("POST", `/reajuste/${lojaId}/publicar`, { fonte, chaves }),
  lotes: (lojaId) => requisicao("GET", `/reajuste/${lojaId}/lotes`),
  lote: (id) => requisicao("GET", `/reajuste/lotes/${id}`),
  desfazerLote: (id) => requisicao("POST", `/reajuste/lotes/${id}/desfazer`),
  importarIfood: (lojaId, merchantId) => requisicao("POST", `/reajuste/${lojaId}/importar-ifood`, { merchantId }),
  precos: (lojaId) => requisicao("GET", `/reajuste/${lojaId}/precos`),
  salvarPrecos: (lojaId, lista) => requisicao("PUT", `/reajuste/${lojaId}/precos`, lista),
};

/** Baixa a planilha .xlsx com os itens da loja para o cliente preencher o custo. */
export async function baixarPlanilhaCustos(lojaId, nomeArquivo) {
  const response = await fetch(`${API_BASE_URL}/itens/planilha-custos?lojaId=${lojaId}`, { headers: getHeaders() });
  if (!response.ok) {
    let msg = `Erro ${response.status} ao gerar a planilha`;
    try {
      const body = await response.json();
      msg = body.mensagem || msg;
    } catch {
      /* corpo não é JSON */
    }
    throw new Error(msg);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = nomeArquivo || `custos-loja-${lojaId}.xlsx`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
