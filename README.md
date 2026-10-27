# Andrades TV

Um ecrã inicial leve e sem anúncios para TVs Android / Google TV, com o visual da Apple TV.

- **Vidro claro de dia, escuro à noite** (muda sozinho às 7h e às 20h).
- **Destaque grande** com as cores e o logótipo do app em foco, e uma frase sobre ele.
- **Doca de vidro fosco** com os favoritos: HBO Max, Disney+, Prime Video, YouTube sem anúncios (SmartTube)
  e NordVPN. Com os apps instalados aparecem os logótipos oficiais; sem eles, um logótipo em texto e "Obter".
- **Todas as apps** numa grelha mais abaixo, por ordem alfabética, com o atalho para as Definições.
- **Estado da VPN** sempre visível no canto superior direito, ao lado do relógio.
- **Protetor de ecrã** com relógio, que muda de sítio a cada minuto.
- **Sem root e reversível:** nada é apagado, e `tools/restaurar.sh` repõe a TV como veio de fábrica.
- **Sem serviços externos:** o app não faz pedidos à internet. Só usa as permissões de ver o estado da rede.

## Preparar a TV

1. *Definições → Sistema / Preferências do dispositivo → Acerca de*: carregue 7 vezes em **Compilação**.
2. *Opções de programador*: ligue a **Depuração USB** e, se existir, o **ADB por rede**.
3. Anote o IP da TV (*Definições → Rede*).

## Compilar

Precisa de um JDK 17+ e do Android SDK. No Mac:

```bash
brew install --cask android-commandlinetools android-platform-tools
sdkmanager --sdk_root=/opt/homebrew/share/android-commandlinetools "build-tools;35.0.0" "platforms;android-34"
./build.sh
```

Cria o `AndradesTV.apk`. A primeira compilação gera a chave `andradestv.keystore`, que fica fora do git.
**Guarde-a:** para atualizar o app na TV é preciso a mesma chave.

## Instalar

```bash
./tools/instalar.sh 192.168.1.50 --smarttube --vpn-sempre --protetor
```

| Opção | O que faz |
|---|---|
| `--smarttube` | Descarrega o SmartTube mais recente do [GitHub oficial](https://github.com/yuliskov/SmartTube) e instala-o. |
| `--vpn-sempre` | Define a NordVPN como "VPN sempre ativa" do Android: liga sozinha quando a TV arranca. Instale primeiro a NordVPN pela Play Store da TV e inicie sessão nela. |
| `--protetor` | Usa o relógio do Andrades TV como protetor de ecrã. |

Na primeira ligação, a TV pergunta se aceita a depuração deste computador: marque *Permitir sempre* e aceite.

O script guarda o estado original da TV em `tools/estado/` e desativa o ecrã inicial da fábrica
(por exemplo a PatchWall da Xiaomi), sem o desinstalar. Sem isso, em muitas TVs o botão Home continua a
abrir o ecrã antigo.

## Voltar ao original

```bash
./tools/restaurar.sh 192.168.1.50                 # repõe o ecrã da fábrica, o protetor e a VPN
./tools/restaurar.sh 192.168.1.50 --desinstalar   # e remove o Andrades TV
```

## Sobre a VPN

O Andrades TV não traz uma VPN própria. Usa a app oficial da NordVPN, que na TV liga com NordLynx
(WireGuard). Com `--vpn-sempre`, o Android mantém-na ligada desde o arranque. O launcher deteta qualquer
VPN ligada (NordVPN ou outra) e mostra o estado.

## Sobre o SmartTube

O SmartTube é um cliente de YouTube de código aberto para Android TV, sem anúncios e com SponsorBlock.
Não está na Play Store e não é oficial da Google; usá-lo vai contra os termos do YouTube. A alternativa
oficial é o YouTube Premium.

## Estrutura

```
app/AndroidManifest.xml
app/res/drawable/               ícone e banner (vetoriais)
app/src/pt/andrades/tv/
  HomeActivity.java             o ecrã inicial: destaque, doca e grelha
  Card.java                     o cartão com o efeito de foco da Apple TV
  Theme.java                    as cores do tema claro e escuro
  Catalog.java                  favoritos e leitura dos apps instalados
  VpnWatcher.java               deteta se há uma VPN ligada
  ClockDream.java               o protetor de ecrã
build.sh                        compila sem Gradle (aapt2, javac, d8, apksigner)
tools/instalar.sh, restaurar.sh
```

Para mudar os favoritos, edite a lista `FAVORITES` em `Catalog.java` e o bloco `<queries>` do manifesto.
