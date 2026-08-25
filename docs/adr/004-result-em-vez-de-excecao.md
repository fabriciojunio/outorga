# ADR 004: Result em vez de exceção para regra de negócio

**Data:** 2026-08-24
**Status:** Aceito

## Contexto

Boa parte do que este sistema faz é recusar coisas: publicar sem licença,
assistir sem assinatura, assistir fora do território, abrir a terceira tela num
plano de duas. Nenhuma dessas recusas é um defeito. São o comportamento
esperado, e o motivo de cada uma precisa chegar ao usuário final.

Modelar isso com exceção tem dois problemas. Exceção não aparece na assinatura
do método, então nada obriga quem chama a tratar. E o custo de montar o stack
trace é desperdício quando o "erro" é a resposta normal.

## Decisão

Operação de negócio devolve `Result<T>`, uma interface selada com `Ok` e `Erro`.
O erro carrega um `FalhaDeNegocio` com código estável, mensagem para gente ler e
um mapa de detalhes.

```java
public Result<Titulo> publicar(Licenca licenca, Instant agora) {
    if (!licenca.vigenteEm(agora)) {
        return Result.erro(new FalhaDeNegocio("LICENCA_NAO_VIGENTE",
                "A licenca nao esta vigente nesta data")
                .com("statusDaLicenca", licenca.status().name()));
    }
    ...
}
```

Exceção continua valendo para o que é defeito de verdade: banco fora do ar,
configuração inválida na subida, bug de programação.

O código da falha é **contrato de API**. Um mapa único em `Respostas` traduz
código para status HTTP, e é a única fonte dessa tradução.

## Consequências positivas

- A assinatura do método diz que aquilo pode dar errado
- O código estável deixa o cliente ramificar sem depender de texto:
  `LIMITE_DE_TELAS` leva a uma tela, `SEM_ASSINATURA` leva a outra
- Um mapa único evita o mesmo erro sair 400 num endereço e 422 no outro
- Testar recusa fica trivial: comparar código, não capturar exceção

## Consequências negativas

- Mais verboso que `throw`. Encadeamento de três operações vira três ifs
- `valorOuFalha()` estoura se chamado num erro. É proposital, mas é uma armadilha
  para quem não conhece o padrão
- Java não tem `?` do Rust nem `do-notation`, então o encadeamento fica manual

## Alternativas consideradas

**Exceção específica por caso.** Idiomático em Java, e foi assim que o
`IllegalArgumentException` acabou ficando nos construtores de objeto de valor.
Para o fluxo de negócio, esconde o que pode dar errado.

**`Optional<Erro>` no retorno.** Perde o valor de sucesso, e metade das
operações precisa devolver a entidade alterada.
