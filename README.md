# Gigamax (Etapa 3: dano compartilhado)
Mod NeoForge 1.21.1 para Cobblemon.

## Como funciona
- Gigantes brilham, soltam particulas e mostram barra de boss (a vida compartilhada) a menos de 64 blocos.
- **Clique com a mao vazia no gigante** para lutar. Cada jogador luta a propria batalha contra um clone
  invisivel e nao capturavel; o dano causado e descontado da vida do gigante. Pode entrar a qualquer momento.
- Quando a vida chega a 0, o gigante cai e quem causou dano recebe as recompensas.
- Spawn natural raro com aviso no chat. Tudo em `config/gigamax.json`: intervalo, chance, distancia, escala,
  tempo de vida, `battlesToKill` (quantas "barras de vida" o gigante tem), `rewards` (item -> quantidade) e especies.
- Comandos (OP): `/gigante <especie> [escala]` e `/gigante recarregar`.

## Compilar sem PC
Suba os arquivos num repositorio do GitHub (mantenha `.github`), rode Actions > build e baixe o artefato **gigamax-jar**.

## Pontos a verificar no primeiro teste
- Nomes da API do Cobblemon (BattleBuilder.pve, CobblemonEvents.BATTLE_VICTORY/BATTLE_FLED, maxHealth/currentHealth).
- A propriedade `uncatchable=yes` do clone.
- Se o dano aparece certo apos a batalha.
