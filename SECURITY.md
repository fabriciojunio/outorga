# Segurança

## Reportar uma falha

Se você encontrou uma vulnerabilidade, não abra issue pública. Escreva para o
e-mail do mantenedor descrevendo o problema, o caminho para reproduzir e o
impacto esperado. A resposta sai em até 72 horas.

## O que já está implementado

### Autenticação e sessão

- Senha com BCrypt de custo 12. Argon2id seria a escolha de manual, e o domínio
  não muda se trocarmos, mas a instância onde isso roda tem 512 MB e o Argon2
  com parâmetro decente come dezenas de megabytes por hash simultâneo. Está
  documentado na própria classe
- Bloqueio da conta por 15 minutos depois de 5 tentativas erradas seguidas
- Conta bloqueada recusa login **mesmo com a senha certa**, até o prazo passar
- Login com e-mail inexistente roda o cifrador contra um hash descartável, para
  que o tempo de resposta não diferencie "usuário não existe" de "senha errada".
  Sem isso, a lista de assinantes de qualquer cliente sai para quem souber medir
  o relógio
- Token de acesso curto, de 15 minutos, sem consulta ao banco
- Refresh de 14 dias com **rotação a cada uso** e estado no banco
- **Detecção de reuso**: se um refresh já usado voltar, todas as sessões daquele
  usuário caem. Ou o token vazou, ou o cliente está com defeito, e nos dois casos
  o certo é cortar
- 401 para quem não se identificou e 403 para quem não tem permissão. São coisas
  diferentes e o cliente precisa distinguir

### Autorização

- RBAC por papel: `ADMIN_PLATAFORMA`, `DONO`, `EDITOR`, `SUPORTE`, `ASSINANTE`
- Checagem em duas camadas: `@PreAuthorize` na rota e verificação explícita
  dentro do caso de uso. Redundante de propósito
- **Todo repositório recebe o tenant na assinatura do método.** Isolamento entre
  clientes não depende de configuração nem de contexto de thread

### Reprodução

- Token de reprodução válido por 5 minutos, para uma sessão
- URL de mídia assinada e de vida curta
- Limite de telas simultâneas por plano, com sessão sem sinal de vida deixando
  de contar depois de 2 minutos
- Limite de aparelhos registrados por conta
- Bloqueio por território e por tipo de aparelho, conforme o contrato
- Controle parental por classificação indicativa, aplicado no catálogo e no play

### HTTP

- API sem estado e sem cookie de sessão. CSRF fica desligado por escolha, não
  por esquecimento: o que protege escrita é o token no cabeçalho, que nenhum
  navegador manda sozinho
- CORS com lista fechada de origens, vinda de variável de ambiente
- HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options: DENY`,
  `Permissions-Policy` e CSP, tanto na API quanto no site
- Erro inesperado devolve um identificador curto e nada mais. O stack trace fica
  no log, com o mesmo identificador
- Sem source map em produção

### Cobrança

- Webhook confere a assinatura **antes** de ler o corpo, com comparação em tempo
  constante
- Nenhum dado de cartão passa pelo sistema
- Processamento idempotente: confirmar duas vezes estende o ciclo a partir do
  fim vigente, não a partir de hoje

### Auditoria

Login aceito e recusado, troca de senha, criação e desativação de conta,
publicação e bloqueio de título, cadastro, comprovação e rescisão de licença,
abertura e cancelamento de assinatura, pagamento confirmado e recusado,
reprodução autorizada e recusada, abertura e suspensão de cliente, exportação e
exclusão de dado pessoal.

A trilha guarda quem, o quê, qual recurso, de qual endereço e quando. **Nunca
guarda senha, token, número de cartão nem o corpo inteiro de uma requisição.** A
lista de ações é um enum fechado: auditoria com texto livre vira lixo em três
meses.

### Configuração

- O servidor recusa iniciar com configuração inválida. Segredo de JWT com menos
  de 64 caracteres é erro de inicialização
- Em `PRODUCAO` sem gateway ou sem armazenamento configurado, a aplicação
  **recusa subir**. Subir com cobrança simulada seria dar assinatura de graça
  para quem clicasse, e o erro só apareceria na conciliação
- Nenhum segredo no repositório. O `render.yaml` marca os sensíveis como
  `sync: false`
- Contêiner roda como usuário sem privilégio

## O que ainda não está feito

Honestidade sobre o estado atual:

- **Sem DRM.** URL assinada de vida curta segura compartilhamento casual, não
  ataque dedicado. Catálogo premium precisaria de Widevine ou FairPlay
- **Sem rate limit por IP na aplicação.** Hoje depende da borda (Cloudflare ou o
  proxy do provedor). O bloqueio por tentativa cobre credential stuffing na
  conta, não varredura ampla
- **Sem verificação de e-mail no cadastro**
- **Sem segundo fator** para conta de painel
- **Sem rotação automática de segredo**
- **Sem teste de restauração de backup documentado.** O backup existe no plano
  do provedor de banco; restaurar nunca foi ensaiado

## Dependências

O CI roda `dependency-check` no backend e `npm audit` na web. Vulnerabilidade
alta ou crítica quebra o build; média e baixa entram no relatório.
