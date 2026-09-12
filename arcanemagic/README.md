# Arcane Magic — Minecraft Java版 26.2 / Fabric 魔法MOD

杖・魔導書・「アーケインの祭壇」を中心にした、シンプルな魔法MODです。

* **杖 (Wand)** — 魔法を1つだけ付与できるが、祭壇で何度でも強化(Lv.1〜5)できる
* **魔導書 (Spellbook)** — 魔法を最大6個まで登録でき、**Ctrl + マウスホイール**(または専用キー)で選択を切り替えられる
* **アーケインの祭壇 (Arcane Altar)** — 杖/魔導書に巻物で魔法を覚えさせたり、杖を強化したりする専用ブロック

内蔵の魔法は6種類: ファイアボール / アイスシャード / ヒール / サンダーストライク / ブリンク / リパルス。

---

## 0. 最初に読んでください

このMODは最初、インターネットに接続できないサンドボックス環境の中で作られたため、
実際のビルド確認ができないまま暫定的なコードで開発をスタートしました。その後、
実際にユーザーの方と一緒に手を動かしてビルドを試したところ、**Minecraft 26.2の
Fabric環境が「Yarn」ではなく「Mojangの公式マッピング」をデフォルトで使うように
なっている**ことが判明したため(クラス名・パッケージ名がまるごと違う体系でした)、
GitHub上の Fabric API の実際のソースコード(`https://github.com/FabricMC/fabric` の
`26.2` ブランチ)を直接確認しながら、全Javaファイルを正しい命名に書き直し済みです。

* **Javaのソースコード・アセット(テクスチャ/モデル/言語ファイル/レシピ)は全て完成しています。**
  このMOD特有のロジック(杖/魔導書/祭壇/6つの魔法/ネットワーク同期/Ctrl+ホイール)はすべて実装済みで、
  実際の26.2向けFabric APIのソースコードで裏を取った命名になっています。
* データの保存方法は、独自のコンポーネント登録をやめて、バニラの汎用NBT保存機能
  (`CustomData`)に間借りする方式に変更しました(詳細は6章)。
* とはいえ、Minecraft本体(Mojangの公式マッピング)側の一部メソッド名は実機で
  裏取りできていない箇所が残っています(Fabric API経由で確認できたものは高い自信が
  ありますが、バニラ単体のクラスはMojangが非公開のためGitHub上で確認できません)。
  数カ所、IDEが「このメソッドは親クラスをオーバーライドしていません」等の赤い警告を
  出す可能性があります。直し方は下の「トラブルシューティング」に書いてあるので、
  心配せずそのまま読み進めてください。
* もし `外部ライブラリ` に Minecraft や fabric-api が1つも表示されない場合は、
  Gradleの同期が中途半端な状態で止まっているサインです。8章の
  「外部ライブラリが空になる場合」を参照してください(実際にこの問題が起きて解決した実例です)。

---

## 1. 手順1: 正式なプロジェクトの土台を生成する(最重要)

1. ブラウザで **https://fabricmc.net/develop/template/** を開く
2. 以下を設定する
   * Mod Name: `Arcane Magic` (何でもOK)
   * Mod ID の下にある **「Use custom id」** を押して **`arcanemagic`**(ハイフンなし)に変更する
   * Package Name: **`com.arcanemagic`**(← このMODのJavaコードがこのパッケージ名を
     前提にしているので、必ず同じにしてください)
   * Minecraft Version: **26.2**
   * Advanced Options の4つのチェックボックス(Kotlin Programming Language /
     Data Generation / Split client and common sources / Kotlin Build Script)は
     **すべてチェックなしのまま**にする(このMODは `src/main/java` に全部まとめる
     単一ソースセット構成が前提です)
3. 「Download Template (.ZIP)」を押してダウンロードして展開する
4. 展開してできたフォルダの中の `gradle.properties` と `build.gradle` が、実際に
   26.2向けにFabricが用意している最新の設定です。この2つのファイルは
   **このMOD側のものと入れ替える必要はありません**。生成されたものをそのまま使ってください
   (このMOD側の `gradle.properties`/`build.gradle` は「だいたいこういう内容になる」という
   参考用に、確認できた実例をそのまま反映してあります)。

   ※ 世代によっては `yarn_mappings` という項目が無いことがあります。これは最近のFabricが
   「Mojangの公式マッピング」をデフォルトで使うようになったためで、異常ではありません
   (詳しくは0章参照)。

---

## 2. 手順2: このフォルダの中身を、生成したテンプレートに上書きする

手順1で展開したテンプレートのフォルダ(以下「テンプレート側」)に、この `arcanemagic/` フォルダ
(以下「MOD側」)の中身を **上書きコピー** します。

| コピー元(MOD側) | コピー先(テンプレート側) | 備考 |
| --- | --- | --- |
| `src/main/java/com/arcanemagic/` | `src/main/java/com/arcanemagic/`(既存の例を削除してから) | テンプレートが生成した`ExampleMod.java`等のサンプルは削除してOK |
| `src/main/resources/fabric.mod.json` | 同名を上書き | |
| `src/main/resources/arcanemagic.mixins.json` | 同名を上書き(既存のExampleMixin例は不要なら削除) | |
| `src/main/resources/assets/arcanemagic/` | 同名フォルダに上書き | |
| `src/main/resources/data/arcanemagic/` | 同名フォルダに上書き | |

生成されたテンプレートの `gradle.properties` を開くと、`version=1.0.0` と
`group=com.arcanemagic` のように書かれているはずです。すでに欲しい値になっているので、
基本的に**何も書き換える必要はありません**。

`build.gradle` と `settings.gradle` も、テンプレート側のものをそのまま使ってください
(このMOD側の同名ファイルは上書きコピーしないでください)。

### フォルダ構成の最終イメージ

```
(テンプレートのルート)/
├── build.gradle          ← テンプレート由来(そのまま)
├── settings.gradle       ← テンプレート由来(そのまま)
├── gradle.properties     ← テンプレート由来(そのまま)
├── gradlew / gradlew.bat / gradle/wrapper/...   ← テンプレート由来(そのまま)
└── src/main/
    ├── java/com/arcanemagic/  ← MOD側からコピー(テンプレの例(ExampleMod.java等)は削除してから)
    └── resources/
        ├── fabric.mod.json           ← MOD側からコピー(上書き)
        ├── arcanemagic.mixins.json   ← MOD側からコピー
        ├── assets/arcanemagic/       ← MOD側からコピー
        └── data/arcanemagic/         ← MOD側からコピー
```

---

## 3. 手順3: ビルドしてみる

1. IntelliJ IDEA(推奨)で、テンプレートのルートフォルダを開く(`build.gradle`を検知して
   自動でGradle同期が始まります)
2. `build.gradle` が `sourceCompatibility = JavaVersion.VERSION_25` のように **25** を
   要求している場合、JDK 25 が必要です。IntelliJの `File > Project Structure > SDKs` の
   「+」→「Download JDK...」から、バージョン25・ベンダー Eclipse Temurin を選んで
   ダウンロードしてください
3. 初回はMinecraftの逆コンパイル等でそこそこ時間がかかります(数分〜二十数分、回線速度次第)。
   進捗バーが消えても裏側で処理が続いていることがあるので、少し余裕を持って待ってください
4. 同期が終わったら、左側のプロジェクトツリーの一番下にある **「外部ライブラリ」** を開いて、
   `minecraft-merged-...` や `fabric-api` 関連のライブラリがちゃんと並んでいるか確認してください。
   **もし1つも表示されていなければ、下の「8. トラブルシューティング」の
   『外部ライブラリが空になる場合』を今すぐ試してください**(これをしないと、この先すべての
   ファイルで「パッケージが存在しません」という大量のエラーが出ます)
5. `Build → Build Project`(`Ctrl+F9`)を実行してコンパイルを試す
6. コンパイルエラーが出た場合は、下の「8. トラブルシューティング」を参照してください

### 実行して動作確認する

* `Gradle > Tasks > fabric > runClient` を実行するとMinecraftクライアントが起動します
* タイトル画面が出れば成功です。「シングルプレイ」→ 新しいワールドを作って入りましょう
* クリエイティブモードのアイテム一覧に「アーケインマジック」タブが増えていればOKです

---

## 4. 手順4: 配布用のjarを作る & 本物のランチャーに導入する

1. `Gradle > Tasks > build > build` を実行する
   (コマンドラインなら、テンプレートのルートで `./gradlew build`)
2. `build/libs/arcanemagic-1.0.0.jar` ができます(`-sources.jar`ではない方)
3. 導入するMinecraft本体側に、以下をすべて用意します
   * **Fabric Loader** をMinecraft 26.2向けにインストール済みであること
     (https://fabricmc.net/use/ からインストーラーを使うのが簡単です)
   * `.minecraft/mods/` フォルダに **Fabric API** の jar(手順1で確認したバージョンに
     対応するもの)を入れる
   * 同じ `.minecraft/mods/` フォルダに、手順4-2で作った `arcanemagic-1.0.0.jar` を入れる
4. Minecraft Launcherで「Fabric Loader 26.2」のプロファイルを起動する

---

## 5. 遊び方

### 入手方法(クリエイティブ)
クリエイティブモードのインベントリの「アーケインマジック」タブから全アイテムを入手できます。

### 入手方法(サバイバル/クラフト)

| アイテム | レシピ |
| --- | --- |
| アーケインクリスタル | アメジストの欠片×2 + グロウストーンダスト×1(シェイプレス、結果2個) |
| 杖 (Wand) | ┌─A / ─S / S─┐ (A=アメジストの欠片, S=棒) の型 |
| 魔導書 (Spellbook) | 本×1 + アメジストの欠片×2 + アーケインクリスタル×1(シェイプレス) |
| アーケインの祭壇 | AAA / ACA / SSS (A=アメジストの欠片, C=アーケインクリスタル, S=石レンガ) |
| 各魔法の巻物 | 紙 + 特定の素材 + アーケインクリスタル(シェイプレス、結果2個) |

巻物の素材対応表:

| 魔法 | 追加素材 |
| --- | --- |
| ファイアボール | ブレイズパウダー |
| アイスシャード | プリズマリンクリスタル |
| ヒール | ガストの涙 |
| サンダーストライク | レッドストーン |
| ブリンク | エンダーパール |
| リパルス | スライムボール |

### 杖の使い方
1. 「アーケインの祭壇」を右クリックしてGUIを開く
2. 左上のスロットに杖、その下のスロットに好きな魔法の巻物を置く
3. 右側のスロットに結果(魔法が付与された杖)がプレビューされ、下に必要経験値レベルが
   表示される
4. 経験値レベルが足りていれば、右側のスロットをクリック(またはシフトクリック)して
   結果を受け取る → 巻物・元の杖が消費され、魔法付きの杖が手に入る
5. 杖を右クリックすると魔法が発動する
6. 同じ祭壇に、杖 + アーケインクリスタル×3個以上 を置くと「強化(レベルアップ)」の
   プレビューが出る。取り出すと杖のレベルが+1される(最大Lv.5)。レベルが上がるほど
   威力が上がり、クールダウンも短くなる

### 魔導書の使い方
1. 祭壇で 魔導書 + 巻物 を同様の手順で合成すると、空いているスロットに魔法が1つ追加される
   (最大6個まで)
2. 魔導書を持った状態で **Ctrl を押しながらマウスホイールを回す** と、登録した魔法を
   1つずつ切り替えられる(アクションバーに「選択中の魔法: 〇〇 (n/6)」と表示される)
3. Ctrl+ホイールがうまく動かない場合(下記トラブルシューティング参照)は、代わりに
   キーバインド(デフォルト: `,` で前へ / `.` で次へ、オプション画面から変更可能)でも
   同じことができます
4. 魔導書を右クリックすると、選択中の魔法が発動する

---

## 6. 実装の仕組み(かんたん解説)

* **データの保存方法**: 杖の「付与された魔法」「レベル」、魔導書の「登録した魔法一覧」
  「選択中スロット」は、独自コンポーネントを新規登録するのではなく、バニラのアイテムが
  最初から持っている汎用NBT保存欄(`CustomData`)に間借りする形で保存しています
  (`component/WandData.java`+`WandDataHelper.java` / `component/SpellbookData.java`+
  `SpellbookDataHelper.java`)。Codec/StreamCodecを自作しなくて済むぶん、環境差の影響を
  受けにくいシンプルな実装にしてあります。
* **魔法の実体**: `spell/Spell.java` インターフェースを6つのクラス(`spell/spells/`以下)が
  実装しています。新しい魔法を追加したい場合はここにクラスを1つ足すだけです(詳しくは下の
  「7-1. 魔法を追加したい場合」)。
* **祭壇のGUI**: バニラの「鍛冶台(Smithing Table)」と同じ考え方で、素材を置く2スロット+
  結果を受け取る1スロットの構成にしています(`screen/ArcaneAltarMenu.java`)。
  専用のGUI背景テクスチャ画像は用意せず、単色の塗りつぶしだけで枠を描いています
  (`screen/ArcaneAltarScreen.java`)。見た目をリッチにしたい場合は、ここを
  `guiGraphics.blit(...)` を使った画像描画に差し替えてください。
* **Ctrl+ホイールの仕組み**: バニラにはマウスホイールの操作を検知する公開APIが無いため、
  `mixin/MouseMixin.java` で `MouseHandler.onScroll` にMixinで割り込んで実現しています。
  Ctrlが押されていて、手に魔導書を持っている時だけ動作し、それ以外は普段どおり
  ホットバーが切り替わります。
* **クライアント→サーバー通信**: 魔法の選択スロットは(チート対策のため)サーバー側が
  正として管理しています。クライアントは「次/前にして」というパケット
  (`network/CycleSpellPayload.java`)を送るだけで、実際にアイテムのデータを書き換えるのは
  サーバー側(`network/ModNetworking.java`)です。
* **クラス名について**: このMODはMinecraft 26.2が採用している「Mojangの公式マッピング」を
  前提に書かれています。杖 = `Player`、ワールド = `Level`、右クリック結果 =
  `InteractionResult`、GUIの土台 = `AbstractContainerMenu`、識別子 = `Identifier`
  (パッケージは `net.minecraft.resources`)など、Yarnマッピング世代の解説記事とは
  クラス名が異なるので、他の情報源と見比べるときは注意してください。

---

## 7. カスタマイズ方法

### 7-1. 魔法を追加したい場合
1. `spell/spells/` に `Spell` インターフェースを実装した新しいクラスを作る
   (既存の`PushSpell.java`などをコピーして書き換えるのが早いです)
2. `spell/SpellRegistry.java` に `public static final Spell ○○ = register(new ○○Spell());` を追加
3. `item/ModItems.java` に対応する巻物アイテムを `registerScroll(...)` で追加
4. `assets/arcanemagic/lang/ja_jp.json` と `en_us.json` に `spell.arcanemagic.○○` と
   `item.arcanemagic.○○_scroll` の翻訳を追加
5. `assets/arcanemagic/models/item/○○_scroll.json` を追加(他の巻物のモデルをコピーして
   テクスチャパスだけ変更)
6. テクスチャは `scripts/gen_textures.py` の `SPELL_COLORS` に色を1行足して再実行するか、
   自分で16x16のPNGを用意して `assets/arcanemagic/textures/item/○○_scroll.png` に置く

魔導書は6スロット固定なので、7個目以降の魔法を作った場合は
`component/SpellbookData.java` の `SLOT_COUNT` を増やせば対応できます。

### 7-2. クールダウンや威力を調整したい場合
各 `spell/spells/*.java` の `baseCooldownTicks()` (20tick=1秒)と `cast()` 内の数値
(ダメージ量・距離・速度など)を直接書き換えてください。杖・魔導書のレベルによる補正は
`Spell.cooldownTicks(int level)`(デフォルト実装)で一括制御しています。

### 7-3. 見た目を良くしたい場合
* `scripts/gen_textures.py` はPILなしでPNGを手書きしているだけの簡易スクリプトです。
  絵心のある方は各PNG(16x16)をお好きなドット絵ソフトで描き直して
  `assets/arcanemagic/textures/` 以下を上書きしてください。
* 祭壇のGUIも同様に、`screen/ArcaneAltarScreen.java` の`drawBackground`を
  画像ブリット方式に差し替えれば、専用デザインのGUIにできます。

---

## 8. トラブルシューティング

### 外部ライブラリが空になる場合(最優先で確認)

Gradleの同期(インポート)が完了したはずなのに、プロジェクトツリー下部の
**「外部ライブラリ」を展開しても Minecraft や fabric-api が1つも無い**場合、同期が
中途半端な状態で止まっています。この場合、あらゆるファイルで
「パッケージ ○○ が存在しません」という大量のエラーが出ますが、**コードの問題ではありません**。

直し方:
1. IntelliJで `File → Close Project`
2. プロジェクトフォルダの中の `.idea` / `.gradle` / `build` フォルダを削除する
   (ソースコードは一切消えないので安全です)
3. もう一度 `File → Open` で同じフォルダを開き直す
4. 進捗バーが完全に消えるまで、いつもより気持ち長めに待つ
5. 「外部ライブラリ」を確認し、`minecraft-merged-...` や `fabric-api` 系のライブラリが
   ずらっと並んでいればOK

### コンパイルエラーが出た場合

Minecraftのバージョンが上がるたびに、一部のメソッド名やクラス名が変わることがあります。
このMODは実際にGitHub上の **Fabric API 26.2ブランチのソースコード**
(`https://github.com/FabricMC/fabric/tree/26.2`)を直接確認しながら、Mojangの公式
マッピング(`Player` / `Level` / `ItemStack` / `Identifier` など)に合わせて書いてあるため、
基本的にはそのままビルドできるはずです。ただし、Fabric APIを経由せずMinecraft本体だけが
持っているメソッド(バニラ単体のクラスはMojangが非公開のため実機で裏取りできていません)で
万が一ズレがあった場合は、以下を疑ってください。いずれも**赤い波線が出ているメソッド名の上で
`Alt+Enter`(IntelliJ)** を押すと修正候補が出ることが多いです。

| 症状 | 疑う場所 | 直し方 |
| --- | --- | --- |
| `WandItem` / `SpellbookItem` の `use` メソッドが「親をオーバーライドしていない」 | `Item` クラスの `use` メソッドの実際のシグネチャ | `Item` クラスにカーソルを合わせて実際の引数・戻り値の型を確認し、それに合わせて書き換える |
| `ArcaneAltarBlock` の `useWithoutItem` / `getMenuProvider` が「親をオーバーライドしていない」 | `Block` クラスの実際のメソッド名 | 同様に実際のシグネチャを確認して合わせる。GUIを開く処理自体(`serverPlayer.openMenu(...)`)は変更不要なはず |
| `getCooldowns().isOnCooldown(stack)` 等でエラー | `ItemCooldowns` のメソッドが `Item` 引数版のみの場合 | `isOnCooldown(stack)` → `isOnCooldown(stack.getItem())`、`addCooldown(stack, ticks)` → `addCooldown(stack.getItem(), ticks)` に書き換える |
| `CustomData.update(...)` が見つからない | `component/WandDataHelper.java` / `SpellbookDataHelper.java` | `net.minecraft.world.item.component.CustomData` クラスをIDEで開き、実際のメソッド名(NBTを更新する系のstaticメソッド)を確認して合わせる |
| `new SmallFireball(level, caster, x, y, z)` でエラー | `spell/spells/FireballSpell.java` | `SmallFireball`のコンストラクタ引数をIDEで確認し、`Vec3`版だったら3引数をまとめる等調整 |
| `caster.teleportTo(...)` が無い | `spell/spells/BlinkSpell.java` | `Entity`にある実際のテレポート用メソッドに差し替える |
| Mixin (`MouseMixin.java`) が `Cannot find target method` 等でクラッシュする | `MouseHandler`クラスの`onScroll`の実際のメソッド名・引数 | IDEで`net.minecraft.client.MouseHandler`を開いて該当メソッド名を確認し、`@Inject(method = "...")`の文字列を修正。**それでも直らない場合は`arcanemagic.mixins.json`の`"client": ["MouseMixin"]`の行を削除してビルドしてください**。Ctrl+ホイールは使えなくなりますが、キーバインド(`,`/`.`)だけで魔導書の魔法切り替えは問題なく動作します |
| `Item.Properties#setId` / `useBlockDescriptionPrefix` が無い | `ModItems.java` / `ModBlocks.java` | それらのメソッド呼び出し部分を削除して `Registry.register(BuiltInRegistries.ITEM, Identifier.of(...), item)` の形に戻す |
| `FabricCreativeModeTab` が見つからない | `item/group/ModItemGroup.java` | `fabric-api`が依存関係に正しく入っているか`build.gradle`を確認。それでもだめならバニラの `CreativeModeTab.builder()` に置き換える |
| `KeyMappingHelper` / `registerKeyMapping` が見つからない | `ArcaneMagicClient.java` | `net.fabricmc.fabric.api.client.keymapping.v1` パッケージが存在するか確認(古いFabric APIでは `client.keybinding.v1.KeyBindingHelper` という名前でした) |

基本方針: **「このアイテム/ブロックは何をしたいか」は変えずに、呼び出しているメソッドの
名前・引数の型だけをIDEの提案に沿って合わせる**、で9割解決します。焦らず1つずつ直せば
問題ありません。

---

## 9. 既知の制約 / 今後の拡張アイデア

* マナ制ではなく「クールダウン制」にしてあります(シンプルさ優先)。マナバーを追加したい
  場合は`WandData`/`SpellbookData`に`mana`フィールドを足し、`use()`内で消費・回復処理を
  書く形になります。
* アーケインの祭壇は閉じると中身のアイテムがその場にドロップします(クラフト台と同じ挙動)。
  据え置き型のインベントリにしたい場合は`BlockEntity`を作って中身を保持する形に変更してください。
  (今回はシンプルさのためあえて`BlockEntity`なしで実装しています)
* 魔導書のスロットは現状「後ろから消せない(追加のみ)」仕様です。特定スロットを削除する
  UIが欲しい場合は`ArcaneAltarMenu`に削除用の触媒アイテムを追加するのがおすすめです。

---

## 10. ファイル構成

```
arcanemagic/
├── README.md                    … このファイル
├── build.gradle / settings.gradle / gradle.properties  … Gradle設定(手順1参照)
├── scripts/gen_textures.py      … プレースホルダーテクスチャ生成スクリプト
└── src/main/
    ├── java/com/arcanemagic/
    │   ├── ArcaneMagic.java            … 共通(サーバー+クライアント)初期化
    │   ├── ArcaneMagicClient.java      … クライアント専用初期化(GUI登録・キーバインド)
    │   ├── block/                      … アーケインの祭壇ブロック
    │   ├── component/                  … 杖/魔導書のデータ保存(CustomData/NBT)
    │   ├── item/                       … 杖・魔導書・巻物・クリスタル
    │   ├── mixin/                      … Ctrl+ホイール検知用Mixin
    │   ├── network/                    … 魔法切替パケット
    │   ├── screen/                     … 祭壇のGUI
    │   ├── spell/                      … 魔法インターフェース+6つの魔法+レジストリ
    │   └── util/                       … 照準(レイキャスト)ユーティリティ
    └── resources/
        ├── fabric.mod.json
        ├── arcanemagic.mixins.json
        ├── assets/arcanemagic/         … 言語ファイル・テクスチャ・モデル・blockstate
        └── data/arcanemagic/           … クラフトレシピ・ルートテーブル
```
