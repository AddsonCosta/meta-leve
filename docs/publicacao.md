# Como publicar o Meta Leve na Play Store

## 1. Conta de desenvolvedor
1. Acesse https://play.google.com/console e crie a conta (pessoal). A taxa é de US$ 25, paga uma vez.
2. Faça a verificação de identidade que o Google pede. Ela é obrigatória para instalar e publicar.
3. Contas pessoais novas precisam de um **teste fechado com pelo menos 12 testadores por 14 dias** antes de liberar a produção. O teste interno (até 100 pessoas) pode ser usado logo.

## 2. Chave de upload
A Play Store assina o app com a chave dela (Play App Signing). Você só precisa de uma **chave de upload**, que pode ser trocada pelo suporte se for perdida.

No computador, com Java instalado:

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Guarde o arquivo `upload.jks` e as senhas num lugar seguro. **Nunca** coloque esse arquivo no repositório.

## 3. Secrets no GitHub
Em GitHub > meta-leve > Settings > Secrets and variables > Actions, crie:

| Secret | Valor |
|---|---|
| `METALEVE_KEYSTORE_BASE64` | conteúdo de `base64 -w0 upload.jks` (no Mac: `base64 -i upload.jks`) |
| `METALEVE_KEYSTORE_PASSWORD` | senha do keystore |
| `METALEVE_KEY_ALIAS` | `upload` |
| `METALEVE_KEY_PASSWORD` | senha da chave |

Com os secrets criados, o build do GitHub Actions passa a gerar o AAB assinado (artefato `meta-leve-release-aab`).

## 4. Criar o app e enviar
1. Na Play Console: **Criar app**, nome "Meta Leve", app gratuito.
2. Preencha a página da loja com os textos de `docs/loja.md`.
3. Em **Política de privacidade**, use o link do arquivo `docs/privacidade.md` (depois de trocar o e-mail de contato).
4. Responda os formulários: segurança dos dados, classificação de conteúdo, público-alvo e app de saúde.
5. Em **Testes > Teste interno**, crie uma versão e envie o `.aab` baixado do GitHub Actions.
6. Adicione seu e-mail como testador e abra o link de participação no celular. O app instala pela própria Play Store.
