# PRD — Agrupamento Automático e Coleções Hierárquicas

## 1. Visão Geral

Esta funcionalidade adiciona ao leitor digital um sistema avançado de organização automática para:

- HQs;
- Mangás;
- Livros;
- Séries;
- Volumes;
- Coleções.

O objetivo principal é reduzir o trabalho manual de organização da biblioteca.

Ao importar um arquivo, o sistema deve tentar identificar:

- qual é a série;
- qual é o número;
- qual é o volume;
- qual é o tipo de publicação;
- a quais coleções ele pode pertencer;
- qual é sua posição correta dentro da organização existente.

O sistema deve manter o princípio:

> Automação ajuda o usuário, mas nunca deve prevalecer sobre uma decisão manual explícita.

---

# 2. Objetivos

## 2.1 Objetivo principal

Permitir que arquivos importados sejam automaticamente organizados em séries e coleções hierárquicas.

Exemplo:

```text
Batman
└── Absolute Batman
    ├── #1
    ├── #2
    ├── #3
    └── #4
```

Ao importar:

```text
Absolute Batman #5.cbz
```

o sistema deve identificar a série e direcionar o arquivo para:

```text
Batman
└── Absolute Batman
    └── #5
```

sem exigir que o usuário reorganize manualmente toda vez.

---

# 3. Princípios

## 3.1 Local-first

Toda a funcionalidade inicial deve funcionar offline.

O agrupamento deve utilizar:

- metadados existentes;
- banco local;
- nome do arquivo;
- pasta de origem;
- regras locais;
- informações anteriormente confirmadas pelo usuário.

Nenhuma conexão externa deve ser obrigatória.

Integrações com fontes externas poderão ser adicionadas posteriormente.

---

## 3.2 Usuário sempre tem prioridade

Uma decisão manual explícita deve prevalecer sobre:

- detecção automática;
- inferência;
- regras;
- reprocessamento.

Exemplo:

```text
Sistema:
Absolute Batman → Batman

Usuário:
remove associação com Batman
```

Resultado:

O sistema deve armazenar uma exceção e não recriar automaticamente essa relação.

---

# 4. Terminologia

## 4.1 Item

Qualquer arquivo importado.

Exemplos:

```text
CBZ
CBR
PDF
EPUB
```

---

## 4.2 Livro / Edição

Representa uma unidade individual de leitura.

Exemplo:

```text
Absolute Batman #1
```

---

## 4.3 Série

Agrupamento lógico de itens relacionados.

Exemplo:

```text
Absolute Batman
```

A série é diferente de uma coleção.

---

## 4.4 Coleção

Agrupamento criado ou confirmado pelo usuário.

Exemplo:

```text
Batman
```

Pode conter:

- livros;
- séries;
- outras coleções.

---

# 5. Exemplo conceitual

```text
📁 Batman
│
├── 📚 Absolute Batman
│   ├── 📖 #1
│   ├── 📖 #2
│   └── 📖 #3
│
├── 📚 Batman (2016)
│   ├── 📖 #1
│   └── 📖 #2
│
└── 📖 Batman: The Killing Joke
```

---

# 6. Estrutura hierárquica

Coleções podem conter:

```text
Collection
├── Collection
├── Series
└── Book
```

Uma coleção não é obrigatoriamente apenas uma pasta de séries.

---

# 7. Múltiplos pais

Uma mesma série ou coleção pode pertencer a múltiplas coleções.

Exemplo:

```text
Batman
└── Absolute Batman
```

e:

```text
Absolute Universe
└── Absolute Batman
```

Ambos apontam para a mesma entidade:

```text
Series: Absolute Batman
```

Os livros não são duplicados.

---

# 8. Modelo hierárquico

A estrutura deve ser tratada conceitualmente como um:

```text
Directed Acyclic Graph — DAG
```

e não como uma árvore simples.

Isso ocorre porque uma entidade pode possuir múltiplos pais.

---

# 9. Ciclos

Ciclos são proibidos.

Exemplo inválido:

```text
Batman
└── DC
    └── Batman
```

Outro exemplo:

```text
A → B
B → C
C → A
```

O sistema deve impedir a operação antes de persistir a relação.

---

# 10. Profundidade máxima

A hierarquia deve possuir profundidade máxima de:

```text
8 níveis
```

Justificativa:

- permite estruturas complexas;
- é superior ao mínimo desejado de 5 níveis;
- evita estruturas extremamente difíceis de navegar;
- reduz complexidade de consultas e interface.

Exemplo:

```text
1 DC
2 Batman
3 Elseworlds
4 Absolute Universe
5 Gotham
6 Batman
7 Subcoleção
8 Série
```

Uma tentativa de criar o nível 9 deve ser bloqueada.

O usuário deve receber uma mensagem informando que o limite foi atingido.

---

# 11. Identificação de série

A principal informação para identificar uma série será:

```text
series
```

Quando disponível em metadados.

Exemplo:

```xml
<Series>Absolute Batman</Series>
```

---

# 12. Ordem de prioridade das informações

A ordem geral para identificação será:

```text
1. Metadados internos
2. Correções manuais
3. Nome do arquivo
4. Nome da pasta
```

Entretanto:

> uma correção manual previamente registrada sempre deve prevalecer sobre futuras automações.

Na prática, a prioridade operacional fica:

```text
Exceção/correção manual
      ↓
Metadados
      ↓
Nome do arquivo
      ↓
Nome da pasta
```

---

# 13. ComicInfo.xml

Para:

```text
CBZ
CBR
```

o sistema deve suportar `ComicInfo.xml`.

Campos relevantes:

```text
Series
Number
Volume
Year
Publisher
StoryArc
SeriesGroup
Format
```

Outros metadados podem ser armazenados, mas não precisam inicialmente participar do agrupamento.

---

# 14. EPUB

EPUB deverá futuramente participar do mesmo sistema.

Metadados possíveis:

```text
title
author
publisher
identifier
series
seriesIndex
subject
```

Entretanto, a primeira fase desta funcionalidade é focada em HQs e mangás.

---

# 15. PDF

PDF deve ser suportado na primeira fase.

Quando possuir metadados adequados, eles devem ser utilizados.

Quando não possuir:

```text
Batman_Absolute_2024_01.pdf
```

o sistema pode inferir informações através do nome.

Dependendo da configuração:

```text
Automático
→ tenta organizar

Perguntar
→ apresenta sugestão

Desativado
→ não organiza
```

---

# 16. Configuração de automação

O usuário deve poder escolher entre três comportamentos.

## Modo automático

```text
Organizar automaticamente
```

Associações consideradas seguras podem acontecer sem intervenção.

Inferências de novas coleções pai continuam dependendo de confirmação quando necessário.

---

## Modo perguntar

```text
Sempre perguntar antes de aplicar sugestões relevantes
```

---

## Modo desativado

```text
Não executar auto-agrupamento
```

O usuário continua podendo organizar manualmente.

---

# 17. Criação de séries

Se:

```text
Series = Absolute Batman
```

mas `Absolute Batman` não existe:

```text
Sistema:
"Série 'Absolute Batman' não encontrada.
Deseja criá-la?"
```

A criação não deve ser automática na primeira versão.

---

# 18. Inferência de coleção pai

Exemplo:

```text
Series = Absolute Batman
```

Sistema infere:

```text
Batman
└── Absolute Batman
```

Mesmo que exista alta confiança, a primeira associação inferida com uma coleção pai deve ser confirmada pelo usuário.

Exemplo:

```text
Sugerimos organizar:

Batman
└── Absolute Batman

Confiança: 94%

[Confirmar]
[Alterar]
[Ignorar]
```

---

# 19. Confiança

O sistema pode calcular uma confiança para a sugestão.

Exemplo:

```text
Série identificada:
Absolute Batman

Confiança:
97%
```

A confiança serve como informação auxiliar.

Ela não deve ser interpretada como garantia de correção.

---

# 20. Identidade vs nome exibido

Séries devem possuir:

```text
canonicalName
displayName
```

Exemplo:

```text
canonicalName:
Absolute Batman

displayName:
Absolute Batman (2024)
```

Alterar o nome visual não deve quebrar o auto-roteamento.

---

# 21. Identificação por hash

Itens devem possuir hash persistente.

Preferência:

```text
SHA-256
```

Exemplo:

```text
fileHash
```

O hash deve identificar o conteúdo independentemente do caminho.

---

# 22. Arquivo movido

Se:

```text
/downloads/Batman001.cbz
```

for movido para:

```text
/HQs/DC/Batman/Batman001.cbz
```

e o hash continuar igual:

O aplicativo deve preservar:

```text
progresso
favoritos
série
coleções
nome customizado
exceções
configurações
```

---

# 23. Duplicatas

Quando um arquivo com o mesmo hash for importado novamente:

```text
"Este conteúdo já existe na biblioteca."

[Substituir]
[Cancelar]
```

O sistema não deve criar automaticamente uma segunda cópia lógica.

---

# 24. Ordenação

Dentro de uma série, a ordenação principal deve utilizar:

```text
Volume
→ Number
```

---

# 25. Tipos de publicação

Itens devem possuir pelo menos:

```text
NORMAL
ANNUAL
SPECIAL
ONE_SHOT
VOLUME
```

---

# 26. Ordem visual dos tipos

A ordem padrão será:

```text
1. Edições normais
2. Annuals
3. Specials
4. One-shots
5. Volumes / encadernados
```

Volumes podem ser apresentados separadamente em área própria.

---

# 27. Edições vs encadernados

Uma série pode conter:

```text
Absolute Batman

Edições
├── #1
├── #2
└── #3

Volumes
└── Vol. 1
```

Isso evita misturar:

```text
Absolute Batman #1
```

com:

```text
Absolute Batman Vol. 1
```

---

# 28. Mangás

Mangás podem ser tratados inicialmente utilizando a mesma lógica das HQs.

Exemplo:

```text
One Piece

Volumes
├── Vol. 103
├── Vol. 104
└── Vol. 105
```

Caso existam:

```text
Chapter 1101
Chapter 1102
```

o comportamento pode ser definido por configuração.

Opções:

```text
Tratar como HQ
```

ou:

```text
Perguntar ao usuário
```

---

# 29. Lacunas

O sistema deve identificar possíveis lacunas.

Exemplo:

```text
#1
#2
#3
#5
```

Resultado:

```text
⚠ Possível edição ausente: #4
```

---

# 30. Onde mostrar lacunas

O aviso deve aparecer:

```text
durante importação
+
na tela da série
```

---

# 31. Falsos positivos

A detecção de lacunas deve ser apresentada como:

```text
"possível edição ausente"
```

e não:

```text
"edição ausente"
```

porque algumas séries podem possuir numeração irregular.

---

# 32. Pesquisa global

Deve existir uma tela separada de:

```text
Séries
```

Além da navegação por coleções.

---

# 33. Pesquisa por coleção

Pesquisar:

```text
Batman
```

deve permitir encontrar descendentes.

Exemplo:

```text
Batman
Absolute Batman
Absolute Batman #1
Absolute Batman #2
Dark Nights: Metal
```

mesmo que a palavra `Batman` não esteja diretamente no título do item descendente.

---

# 34. Filtros

Filtros obrigatórios:

## Tipo

```text
Normal
Annual
Special
One-shot
Volume
```

## Formato

```text
EPUB
PDF
CBZ
CBR
```

## Organização

```text
Coleção
Série
```

---

# 35. Filtros combináveis

O usuário poderá combinar filtros.

Exemplo:

```text
Coleção = Batman
AND
Tipo = Annual
AND
Formato = CBZ
```

---

# 36. Interface

A UI deve diferenciar visualmente os tipos.

Exemplo:

```text
📁 Coleção

📚 Série

📖 Livro/HQ
```

Ícones específicos podem mudar posteriormente, mas a distinção visual deve existir.

---

# 37. Importação imediata

O arquivo deve aparecer na biblioteca antes do término de todo o processo de organização.

Fluxo:

```text
arquivo importado
      ↓
aparece na biblioteca
      ↓
processamento de organização
      ↓
série/coleção atualizadas
```

A organização não deve bloquear o acesso ao conteúdo.

---

# 38. Estado do processamento

Itens podem possuir estados como:

```text
PENDING
PROCESSING
ORGANIZED
NEEDS_REVIEW
FAILED
```

---

# 39. Importação em massa

O sistema deve suportar importação de grandes quantidades.

Exemplo:

```text
247 arquivos encontrados

183 identificados
42 precisam de revisão
12 possíveis duplicatas
7 sem série
3 inválidos
```

---

# 40. Aprovação por grupo

Não perguntar item por item em importações grandes.

Exemplo:

```text
Absolute Batman
20 arquivos

Série:
Absolute Batman

Coleção sugerida:
Batman

[Aplicar aos 20]
```

---

# 41. Decisão aplicada ao grupo

Se o usuário confirmar:

```text
Absolute Batman #1
→ Absolute Batman
→ Batman
```

o sistema deve oferecer:

```text
Aplicar esta decisão aos outros 19 itens desta série?
```

---

# 42. Retomada de importação

Importações interrompidas devem ser retomáveis.

Exemplo:

```text
342 / 800 processados
```

Após reabrir:

```text
Existe uma importação incompleta.

[Continuar]
[Cancelar]
```

---

# 43. Escala

A arquitetura deve suportar:

```text
20.000 itens
```

sem degradação significativa das operações principais.

---

# 44. Performance

O sistema não deve carregar toda a biblioteca em memória.

Utilizar:

```text
paginação
índices de banco
queries específicas
processamento incremental
```

---

# 45. Exceções manuais

Quando o usuário desfizer uma associação automática:

```text
Book X
não pertence a Batman
```

o sistema deve registrar uma exceção persistente.

---

# 46. Exceções não são regras globais

Corrigir:

```text
Absolute Batman #1
```

não significa automaticamente criar:

```text
todos Absolute Batman != Batman
```

A correção vale somente para aquilo que o usuário explicitamente alterou.

---

# 47. Reprocessamento

Deve existir funcionalidade:

```text
Reprocessar organização
```

Pode ser aplicada futuramente a:

```text
item
série
coleção
biblioteca
```

---

# 48. Reprocessamento e correções

Reprocessamento nunca deve sobrescrever:

```text
correções manuais
exceções
```

---

# 49. Regras personalizadas

Não fazem parte da Fase 1.

Entram posteriormente.

Exemplo futuro:

```text
SE
Series = "Absolute Batman"

ENTÃO
Adicionar em Batman
```

---

# 50. Regras múltiplas

Quando implementadas, várias regras poderão ser aplicadas ao mesmo item.

Exemplo:

```text
Series = Absolute Batman
→ Batman

Publisher = DC
→ DC Comics
```

Resultado:

```text
item pertence às duas coleções
```

---

# 51. Ações das regras

Regras futuras poderão:

```text
adicionar vínculo
remover vínculo
criar coleção
```

---

# 52. Escopo das regras

Regras poderão possuir escopo.

Exemplos:

```text
biblioteca inteira
HQs
mangás
EPUB
PDF
pasta
importação
```

---

# 53. Conflito regra vs usuário

Prioridade:

```text
Usuário
>
Regra
```

Uma regra não pode recriar uma relação removida manualmente.

---

# 54. Regra inválida

Se uma regra tentar:

```text
criar ciclo
```

ou:

```text
ultrapassar profundidade
```

a regra deve:

```text
ser ignorada
+
mostrar aviso
```

A importação do arquivo continua.

---

# 55. Exclusão de coleção

Ao excluir uma coleção que contém filhos:

```text
Excluir coleção Batman?

○ Remover apenas esta coleção/vínculos
○ Remover também estrutura associada
[Cancelar]
```

O sistema deve deixar claro o impacto.

---

# 56. Exclusão de série

Ao excluir uma série:

```text
Excluir série Absolute Batman?
```

o sistema deve perguntar se o usuário deseja:

```text
manter os livros na biblioteca
```

ou:

```text
remover também os livros
```

---

# 57. Arquivos físicos

Excluir uma estrutura de organização não deve apagar arquivos físicos automaticamente.

Qualquer operação que possa excluir arquivos deve exigir confirmação explícita.

---

# 58. Múltiplos vínculos

Remover:

```text
Absolute Batman → DC
```

não deve remover:

```text
Absolute Batman → Batman
Absolute Batman → Absolute Universe
```

Cada vínculo é independente.

---

# 59. Histórico

Não será mantido histórico detalhado de decisões automáticas.

O sistema precisa persistir apenas o necessário para:

```text
estado atual
correções
exceções
```

---

# 60. Sincronização

A sincronização desta organização entre dispositivos fica fora do escopo atual.

Futuramente deverão ser avaliados:

```text
coleções
hierarquia
séries
regras
exceções
```

---

# 61. Conflitos futuros de sincronização

Quando dois dispositivos criarem alterações conflitantes:

```text
perguntar ao usuário
```

é a estratégia preferida.

---

# 62. Modelo conceitual

## Book

```text
Book
-------------------
id
fileHash
filePath
title
canonicalTitle
fileType
seriesId
volume
number
publicationType
year
processingStatus
dateAdded
```

---

## Series

```text
Series
-------------------
id
canonicalName
displayName
year
publisher
createdAt
```

---

## Collection

```text
Collection
-------------------
id
name
createdAt
updatedAt
```

---

## CollectionRelation

Representa relações hierárquicas.

```text
CollectionRelation
-------------------
id
parentCollectionId
childType
childId
createdAt
```

`childType` pode ser:

```text
COLLECTION
SERIES
BOOK
```

---

# 63. Livro em múltiplas coleções

Uma relação separada deve permitir:

```text
Book ↔ Collection
```

muitos-para-muitos.

---

# 64. Série em múltiplas coleções

Também deve existir:

```text
Series ↔ Collection
```

muitos-para-muitos.

---

# 65. ManualOverride

Exemplo conceitual:

```text
ManualOverride
-------------------
id
entityType
entityId
relationType
targetId
action
createdAt
```

Actions:

```text
FORCE_ADD
FORCE_REMOVE
```

Isso permite registrar decisões manuais sem alterar os metadados originais.

---

# 66. ImportSession

Necessária para retomada.

```text
ImportSession
-------------------
id
status
totalItems
processedItems
createdAt
updatedAt
```

---

# 67. ImportItem

```text
ImportItem
-------------------
id
sessionId
bookId
status
detectedSeries
confidence
requiresReview
```

---

# 68. Fluxo de processamento

```text
Arquivo importado
      ↓
Hash
      ↓
Detecção de duplicata
      ↓
Extração de metadados
      ↓
Identificação de tipo
      ↓
Identificação da série
      ↓
Busca de série existente
      ↓
Sugestão de associação
      ↓
Aplicação de exceções
      ↓
Validação estrutural
      ↓
Persistência
```

---

# 69. Fluxo principal de aceitação

Entrada:

```text
Absolute Batman #1.cbz
```

Metadados:

```text
Series = Absolute Batman
Number = 1
```

Sistema:

```text
Série encontrada/criada:
Absolute Batman

Coleção sugerida:
Batman
```

Usuário:

```text
Confirmar
```

Resultado:

```text
Batman
└── Absolute Batman
    └── #1
```

Depois:

```text
Absolute Batman #2.cbz
```

Resultado esperado:

```text
Batman
└── Absolute Batman
    ├── #1
    └── #2
```

Busca:

```text
Batman
```

deve encontrar ambos.

Esse fluxo representa o principal critério de sucesso da funcionalidade.

---

# 70. Fases de implementação

## Fase 1 — HQ e Mangá

Prioridade máxima.

Formatos:

```text
CBZ
CBR
PDF
```

Implementar:

- Series;
- Collections;
- hierarquia;
- múltiplos pais;
- ComicInfo.xml;
- detecção de série;
- confirmação;
- organização automática;
- tipos de publicação;
- volumes;
- lacunas;
- pesquisa;
- filtros;
- importação em massa;
- aprovação por grupos;
- retomada;
- exceções manuais;
- reprocessamento básico.

---

## Fase 2 — Refinamento

Adicionar:

- heurísticas melhores de nomes;
- melhor classificação de mangás;
- UX avançada;
- mais opções de reprocessamento;
- regras simples configuráveis.

---

## Fase 3 — Livros

Expandir para:

```text
EPUB
```

e melhorar PDF tradicional.

Adicionar:

- séries literárias;
- autores;
- volumes;
- publisher;
- ISBN quando disponível.

---

## Fase 4 — Regras avançadas

Implementar:

```text
IF metadata
THEN actions
```

com:

- escopo;
- múltiplas ações;
- prioridade;
- validação.

---

## Fase 5 — Fontes externas

Opcional.

Permitir consultas externas somente com autorização do usuário.

Possíveis objetivos:

- corrigir metadata;
- identificar série;
- descobrir número;
- publisher;
- capa;
- ISBN.

A aplicação continua funcional offline.

---

# 71. Requisitos funcionais principais

## RF-001

O sistema deve permitir criar coleções.

## RF-002

O sistema deve permitir criar séries.

## RF-003

Coleções devem poder conter outras coleções.

## RF-004

Coleções devem poder conter séries.

## RF-005

Coleções devem poder conter livros diretamente.

## RF-006

Uma série deve poder pertencer a múltiplas coleções.

## RF-007

Um livro deve poder pertencer a múltiplas coleções.

## RF-008

O sistema deve impedir ciclos.

## RF-009

A profundidade não pode exceder 8 níveis.

## RF-010

O sistema deve ler ComicInfo.xml.

## RF-011

O sistema deve identificar séries através dos metadados.

## RF-012

O sistema deve solicitar confirmação ao criar nova série.

## RF-013

O sistema deve solicitar confirmação ao inferir coleção pai.

## RF-014

O sistema deve preservar correções manuais.

## RF-015

O sistema deve detectar duplicatas por hash.

## RF-016

O sistema deve perguntar antes de substituir duplicata.

## RF-017

O sistema deve detectar lacunas numéricas.

## RF-018

O sistema deve suportar importação em massa.

## RF-019

O sistema deve permitir aprovação em grupo.

## RF-020

O sistema deve permitir retomada de importação.

## RF-021

O sistema deve permitir filtros combinados.

## RF-022

O sistema deve possuir busca por descendentes.

## RF-023

O sistema deve permitir reprocessamento.

---

# 72. Requisitos não funcionais

## RNF-001 — Offline

Todo o fluxo da Fase 1 deve funcionar sem internet.

---

## RNF-002 — Escala

Suportar biblioteca com:

```text
20.000 itens
```

---

## RNF-003 — Responsividade

Abrir a tela de biblioteca não deve exigir processar toda a hierarquia.

---

## RNF-004 — Consistência

Nenhuma operação deve permitir ciclos.

---

## RNF-005 — Resiliência

Importações interrompidas devem ser recuperáveis.

---

## RNF-006 — Não destrutivo

Organização automática nunca deve apagar arquivo físico.

---

## RNF-007 — Determinismo

Dadas:

```text
mesmas informações
+
mesmas regras
+
mesmas exceções
```

o resultado do reprocessamento deve ser previsível.

---

# 73. Casos de borda

Devem ser testados:

```text
Series ausente
Series vazia
Number ausente
Volume ausente
Number decimal
Annual
Special
One-shot
arquivo duplicado
ComicInfo inválido
arquivo sem metadata
nome ambíguo
duas séries com mesmo nome
hierarquia máxima
tentativa de ciclo
livro em múltiplas coleções
série em múltiplas coleções
arquivo movido
arquivo renomeado
importação interrompida
correção manual
reprocessamento
```

---

# 74. Caso: duas séries com mesmo nome

Exemplo:

```text
Batman (2011)
Batman (2016)
```

Metadata:

```text
Series = Batman
```

Se houver:

```text
Volume
Year
```

usar essas informações para tentar desambiguar.

Se não houver confiança suficiente:

```text
Perguntar ao usuário
```

Nunca escolher arbitrariamente.

---

# 75. Critério de confiança

Sugestões ambíguas não devem ser tratadas como certezas.

Exemplo:

```text
Batman

Possíveis séries:

Batman (2011)
Batman (2016)

[Escolher]
```

---

# 76. Critérios de aceitação da Fase 1

A funcionalidade pode ser considerada pronta quando:

1. Um CBZ com ComicInfo.xml é importado.
2. O sistema identifica a série.
3. Se ela não existir, pergunta se deve criá-la.
4. O sistema sugere uma coleção pai.
5. O usuário confirma.
6. Um segundo item da mesma série é importado.
7. Ele é associado corretamente.
8. A ordem Volume + Number é mantida.
9. Annuals e Specials ficam separados.
10. Uma edição ausente gera aviso.
11. A pesquisa por coleção encontra descendentes.
12. Um livro pode aparecer em múltiplas coleções.
13. Uma correção manual não é revertida.
14. Arquivo movido mantém organização e progresso.
15. Duplicata por hash gera confirmação.
16. Importações em massa podem ser aprovadas por grupo.
17. Importação interrompida pode ser retomada.
18. Ciclos são bloqueados.
19. O limite de profundidade é respeitado.
20. A biblioteca continua utilizável com aproximadamente 20.000 itens.

---

# 77. Complexidade técnica estimada

| Componente | Complexidade |
|---|---:|
| Série básica | 3/10 |
| Coleções | 3/10 |
| Muitos-para-muitos | 4/10 |
| Hierarquia | 5/10 |
| DAG + prevenção de ciclos | 6/10 |
| ComicInfo parser | 4/10 |
| Auto-identificação | 6/10 |
| Inferência por nome | 7/10 |
| Importação em massa | 6/10 |
| Retomada | 7/10 |
| Reprocessamento | 6/10 |
| Detecção de lacunas | 4/10 |
| Regras customizadas | 7/10 |
| Sincronização futura | 9/10 |

---

# 78. Riscos principais

## Risco 1 — Metadata ruim

Muitos arquivos terão metadados:

- ausentes;
- incompletos;
- inconsistentes.

Mitigação:

```text
metadata
→ filename
→ folder
→ confirmação
```

---

## Risco 2 — Automação excessiva

O sistema pode organizar incorretamente.

Mitigação:

```text
confiança
+
confirmação
+
desfazer
+
exceções
```

---

## Risco 3 — UX complexa

Coleções com múltiplos pais podem confundir.

Mitigação:

```text
ícones diferentes
breadcrumbs
tela global de séries
```

---

## Risco 4 — Modelo hierárquico

Uma implementação ingênua baseada apenas em:

```text
parentId
```

não suportará adequadamente múltiplos pais.

A estrutura deve ser baseada em relações.

---

## Risco 5 — Performance

Consultas recursivas podem ficar caras.

Mitigação:

- índices;
- queries específicas;
- paginação;
- evitar resolver toda a DAG desnecessariamente;
- caches derivados quando necessário.

---

# 79. Decisão arquitetural crítica

Não modelar:

```text
Collection
- parentCollectionId
```

como única forma de hierarquia.

Isso criaria uma árvore e impediria múltiplos pais.

Preferir:

```text
Collection
+
CollectionRelation
```

---

# 80. Recomendação de arquitetura

Separar o recurso em módulos conceituais:

```text
library
│
├── metadata
├── series
├── collections
├── organization
├── import
└── search
```

O motor de leitura não deve depender diretamente desse sistema.

---

# 81. Serviço conceitual

Exemplo:

```text
OrganizationEngine
```

Responsabilidades:

```text
analisar metadata
identificar série
calcular confiança
resolver associações
aplicar exceções
validar hierarquia
```

---

# 82. Separação importante

Não misturar:

```text
metadata extraction
```

com:

```text
organization decision
```

Exemplo:

```text
ComicInfoParser
        ↓
Metadata
        ↓
OrganizationEngine
        ↓
Suggestion
```

Isso facilita testes e evolução futura.

---

# 83. Resultado de análise

O motor deve produzir sugestões antes de modificar diretamente as entidades.

Exemplo conceitual:

```text
OrganizationSuggestion
-------------------
series
collectionCandidates
confidence
publicationType
warnings
requiresConfirmation
```

---

# 84. Consideração crítica

A funcionalidade planejada é significativamente mais complexa do que um sistema comum de:

```text
pastas
+
tags
```

Ela envolve:

```text
DAG
detecção automática
heurísticas
múltiplos pais
estado manual
exceções
importação assíncrona
retomada
reprocessamento
```

Por isso, a divisão em fases é importante.

---

# 85. Vantagens da solução

- organização quase automática;
- ótima experiência para bibliotecas grandes;
- especialmente útil para HQs e mangás;
- reduz trabalho repetitivo;
- mantém controle do usuário;
- suporta estruturas complexas;
- permite expansão futura;
- combina bem com uma biblioteca local-first.

---

# 86. Desvantagens

- aumenta bastante a complexidade do banco;
- importação passa a possuir mais estados;
- heurísticas nunca serão 100% confiáveis;
- múltiplos pais tornam a UX mais difícil;
- regras futuras adicionam mais complexidade;
- exige muitos testes de casos extremos.

---

# 87. Recomendação de produto

A Fase 1 não deve tentar ser um equivalente completo ao sistema de organização de Calibre, Kavita ou Komga.

Ela deve resolver muito bem:

> "Importei minhas HQs e mangás e o aplicativo conseguiu montar e manter minhas séries organizadas com o mínimo possível de trabalho manual."

Somente depois disso devem entrar:

```text
regras avançadas
integrações externas
sincronização
metadata online
```

---

# 88. Definição de sucesso

A funcionalidade será bem-sucedida se um usuário puder importar uma pasta grande contendo HQs e mangás e, após poucas confirmações agrupadas, obter algo semelhante a:

```text
📁 Batman
│
├── 📚 Absolute Batman
│   ├── Edições
│   │   ├── #1
│   │   ├── #2
│   │   └── #3
│   │
│   ├── Annuals
│   ├── Specials
│   └── Volumes
│
└── 📚 Batman (2016)

📁 Absolute Universe
└── 📚 Absolute Batman
```

sem duplicar arquivos e mantendo todas as associações consistentes.

---

# 89. Prioridade recomendada de implementação

```text
1. Modelo Series
2. Modelo Collection
3. Relações muitos-para-muitos
4. Validação DAG/ciclos
5. ComicInfo.xml
6. Organização manual
7. Auto-identificação de série
8. Sugestões
9. Confirmação
10. Importação em massa
11. Tipos de publicação
12. Lacunas
13. Busca e filtros
14. Reprocessamento
15. Exceções manuais
16. Regras customizadas
```

---

# 90. Resumo executivo

A funcionalidade será construída como um sistema local de organização inteligente.

O conceito central é:

```text
ARQUIVO
  ↓
METADATA
  ↓
SÉRIE
  ↓
ENGINE DE ORGANIZAÇÃO
  ↓
SUGESTÃO
  ↓
CONFIRMAÇÃO / AUTOMAÇÃO
  ↓
COLEÇÃO HIERÁRQUICA
```

Com uma regra fundamental:

```text
DECISÃO MANUAL DO USUÁRIO
>
QUALQUER AUTOMAÇÃO
```

A primeira entrega será focada em:

```text
HQ
+
MANGÁ
+
CBZ
+
CBR
+
PDF
```

e deverá oferecer uma experiência suficientemente robusta para organizar bibliotecas com até aproximadamente:

```text
20.000 itens
```

sem exigir backend ou internet.