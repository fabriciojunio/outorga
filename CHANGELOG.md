# Changelog

## 0.1.0 — 2026-08-24

Primeira versão. Plataforma de streaming white-label multi-tenant com gate de
conteúdo.

### Domínio

- Cliente (tenant) com marca, domínio próprio, período de teste e suspensão
- Licença com titular, contrato, territórios, janela, dispositivos autorizados e
  comprovação anexada
- Catálogo de filmes e séries com temporadas, episódios e classificação
  indicativa brasileira
- Canais ao vivo com grade de programação e detecção de choque de horário
- Planos, cupons e assinatura com período de teste, carência por inadimplência e
  cancelamento com acesso até o fim do ciclo pago
- Contas com perfis, PIN, controle parental e aparelhos registrados
- Política de reprodução com oito verificações encadeadas, cada uma com motivo
  próprio

### Gate de conteúdo

- `Titulo.publicar` como única porta para o ar, exigindo licença vigente
- Varredura horária que bloqueia o que perdeu licença e devolve ao ar o que foi
  renovado
- Rescisão de licença com efeito imediato sobre títulos e canais
- Novo controle de direitos a cada pedido de reprodução
- Painel de licenças vencendo nos próximos 60 dias
- Trilha de auditoria de tudo que envolve direito

### Aplicação

- Autenticação com JWT curto e refresh rotativo com detecção de reuso
- Bloqueio de conta por tentativa, com resposta em tempo constante para e-mail
  inexistente
- Cobrança pelo Asaas com webhook autenticado e idempotente
- Entrega de vídeo em object storage compatível com S3, com URL assinada de vida
  curta e assinatura SigV4 escrita à mão
- Modo demonstração que roda a plataforma inteira sem fornecedor externo
- LGPD: exportação e exclusão por anonimização
- Rotinas de encerramento de assinatura, limpeza de sessão e de token

### Interface

- Vitrine com catálogo, busca, canais e planos
- Player HLS com sinal de vida e encerramento de sessão
- Painel do operador com licenças, catálogo e alerta de vencimento
- Área da conta com perfis, aparelhos, assinatura e direitos do titular

### Infraestrutura

- PostgreSQL com SQL explícito e Flyway
- Dockerfile em duas etapas, usuário sem privilégio, JVM ajustada para 512 MB
- Blueprint do Render e configuração da Vercel
- CI com testes, cobertura mínima de 80% no domínio, build da imagem e varredura
  de dependências

### Testes

- 260 testes
- 22 contra um PostgreSQL de verdade, subido pelo próprio teste, sem Docker
- 20 de ponta a ponta com a aplicação inteira de pé

### Bugs pegos durante a construção

Registrados porque cada um justifica a existência de um tipo de teste:

- `Territorio` não carregava por ordem de inicialização estática. Pego pelo teste
  de domínio
- Canal tirado do ar pelo operador voltava sozinho na varredura de direitos.
  Pego pelo teste de domínio
- `Tenant` gravava o motivo da suspensão e não lia de volta. Pego pelo teste de
  persistência contra PostgreSQL
- Requisição sem token devolvia 403 em vez de 401. Pego pelo teste de ponta a
  ponta
