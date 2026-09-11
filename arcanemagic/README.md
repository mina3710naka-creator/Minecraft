# Arcane Magic — Minecraft Java版 26.2 / Fabric 魔法MOD

杖・魔導書・「アーケインの祭壇」を中心にした、シンプルな魔法MODです。

* **杖 (Wand)** — 魔法を1つだけ付与できるが、祭壇で何度でも強化(Lv.1〜5)できる
* **魔導書 (Spellbook)** — 魔法を最大6個まで登録でき、**Ctrl + マウスホイール**(または専用キー)で選択を切り替えられる
* **アーケインの祭壇 (Arcane Altar)** — 杖/魔導書に巻物で魔法を覚えさせたり、杖を強化したりする専用ブロック

内蔵の魔法は6種類: ファイアボール / アイスシャード / ヒール / サンダーストライク / ブリンク / リパルス。

---

## 0. 最初に読んでください(重要・正直な前提)

このMODは **インターネットに接続できないサンドボックス環境の中で** 作られました。
Minecraft本体・Fabric Loader・Fabric API・Yarnマッピングは実際にダウンロードして
ビルド確認することができなかったため、以下を正直にお伝えします。

* **Javaのソースコード・アセット(テクスチャ/モデル/言語ファイル/レシピ)は全て完成しています。**
  このMOD特有のロジック(杖/魔導書/祭壇/6つの魔法/ネットワーク同期/Ctrl+ホイール)はすべて実装済みです。
* **`build.gradle` / `gradle.properties` のバージョン番号(Fabric Loader・Fabric API・
  Yarnマッピングのバージョン)は「暫定値」です。** 26.2用の正確な最新値を取得できなかったため、
  手順1で必ずご自身で正しい値に差し替えてください(2〜3分で終わります)。
* Minecraftは頻繁に内部API名を細かく変更するため(特に「アイテムを右クリックした時に呼ばれる
  メソッドの型」「ブロックを右クリックした時に呼ばれるメソッドの型」など)、**手元でビルドした時に
  数カ所、IDEが「このメソッドは親クラスをオーバーライドしていません」等の赤い警告を出す可能性が
  あります。** これは仕様変更でよくあることで、直し方は下の「トラブルシューティング」に
  具体的に書いてあります。心配せずそのまま読み進めてください。

要するに: **「魔法MODとしての中身」は完成品、「Minecraft本体との接続部分(Gradleのバージョン番号と
一部メソッドのシグネチャ)」だけはお使いの環境で最終確認・微調整が必要** という状態です。

---

## 1. 手順1: 正式なプロジェクトの土台を生成する(最重要)

1. ブラウザで **https://fabricmc.net/develop/template/** を開く
2. 以下を設定する
   * Minecraft version: **26.2**
   * Mod name: `Arcane Magic` (何でもOK)
   * Package name: **`com.arcanemagic`**(← このMODのJavaコードがこのパッケージ名を
     前提にしているので、必ず同じにしてください。違う名前にした場合は
     `src/main/java` 以下の全ファイルと `fabric.mod.json` の
     `com.arcanemagic.ArcaneMagic` / `com.arcanemagic.ArcaneMagicClient` を
     一括置換してください)
   * Mod ID: **`arcanemagic`**
   * Split source sets: **オフ(チェックを外す)** — このMODは `src/main/java` に
     全部まとめる前提で作ってあります
   * Use Mixins: **オン**
   * Use AccessWidener: オフでOK(使っていません)
   * Depend on Fabric API: **オン**
3. 「Generate」→ ZIPをダウンロードして展開する
4. 展開してできたフォルダの中の `gradle.properties` を開き、そこに書かれている
   `minecraft_version` / `yarn_mappings` / `loader_version` / `fabric_version` /
   `loom_version` の5つの値をコピーしておく

これで「26.2向けに実在する正しいバージョン番号」が手に入ります。

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

そのあと、**テンプレート側の `gradle.properties`** を開き、`mod_version` / `maven_group` /
`archives_base_name` の3行だけを、このMOD側の `gradle.properties` にある値
(`1.0.0` / `com.arcanemagic` / `arcanemagic`)に書き換えてください。
`minecraft_version` 等の4つはテンプレート側の値(手順1で取得した正しい値)を**そのまま残して
ください**(上書きしない)。

`build.gradle` と `settings.gradle` はテンプレート側のものをそのまま使えばOKです
(内容はほぼ同じです)。もし見比べて差分が気になる場合は、MOD側の `build.gradle` を参考にしても
構いません。

### フォルダ構成の最終イメージ

```
(テンプレートのルート)/
├── build.gradle
├── settings.gradle
├── gradle.properties          ← minecraft_version等はテンプレ由来、mod_version等はMOD側の値
├── gradlew / gradlew.bat / gradle/wrapper/...   ← テンプレート由来(そのまま)
└── src/main/
    ├── java/com/arcanemagic/  ← MOD側からコピー
    └── resources/
        ├── fabric.mod.json           ← MOD側からコピー(上書き)
        ├── arcanemagic.mixins.json   ← MOD側からコピー
        ├── assets/arcanemagic/       ← MOD側からコピー
        └── data/arcanemagic/         ← MOD側からコピー
```

---

## 3. 手順3: ビルドしてみる

1. IntelliJ IDEA(推奨)で、テンプレートのルートフォルダを開く(`build.gradle`を検知して
   自動でGradle同期が始まります。JDK 21を使うか聞かれたら21を選択)
2. 初回はMinecraftの逆コンパイル等でそこそこ時間がかかります(数分〜十数分、回線速度次第)
3. 同期が終わったら、右側のGradleタブから `arcanemagic > Tasks > fabric > genSources` を
   実行(ソースコード補完が効くようになります。必須ではありませんが強く推奨)
4. コンパイルエラーが出た場合は、下の「7. トラブルシューティング」を参照してください

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
  「選択中スロット」は、NBTではなく最近のMinecraft(1.20.5以降)の**データコンポーネント**
  という仕組みで保存しています(`component/WandData.java` / `component/SpellbookData.java`)。
* **魔法の実体**: `spell/Spell.java` インターフェースを6つのクラス(`spell/spells/`以下)が
  実装しています。新しい魔法を追加したい場合はここにクラスを1つ足すだけです(詳しくは下の
  「7-1. 魔法を追加したい場合」)。
* **祭壇のGUI**: バニラの「鍛冶台(Smithing Table)」と同じ考え方で、素材を置く2スロット+
  結果を受け取る1スロットの構成にしています(`screen/ArcaneAltarScreenHandler.java`)。
  専用のGUI背景テクスチャ画像は用意せず、単色の塗りつぶしだけで枠を描いています
  (`screen/ArcaneAltarScreen.java`)。見た目をリッチにしたい場合は、ここを
  `context.drawTexture(...)` を使った画像描画に差し替えてください。
* **Ctrl+ホイールの仕組み**: バニラにはマウスホイールの操作を検知する公開APIが無いため、
  `mixin/MouseMixin.java` で `Mouse.onMouseScroll` にMixinで割り込んで実現しています。
  Ctrlが押されていて、手に魔導書を持っている時だけ動作し、それ以外は普段どおり
  ホットバーが切り替わります。
* **クライアント→サーバー通信**: 魔法の選択スロットは(チート対策のため)サーバー側が
  正として管理しています。クライアントは「次/前にして」というパケット
  (`network/CycleSpellPayload.java`)を送るだけで、実際にアイテムのデータを書き換えるのは
  サーバー側(`network/ModNetworking.java`)です。

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

## 8. トラブルシューティング(コンパイルエラーが出た時)

Minecraftのバージョンが上がるたびに、ごく一部のメソッドの「引数の型」や「戻り値の型」が
変わることがあります。このMODは1.20.5〜1.21系で安定していたAPIの形を基準に書いていますが、
26.2で万が一ズレていた場合は、以下の場所を疑ってください。いずれも**赤い波線が出ている
メソッド名の上で `Alt+Enter`(IntelliJ)** を押すと修正候補が出ることが多いです。

| 症状 | 疑う場所 | 直し方 |
| --- | --- | --- |
| `WandItem` / `SpellbookItem` の `use` メソッドが「親をオーバーライドしていない」 | `Item` クラスの `use` メソッドの実際のシグネチャ | `Item` クラスにカーソルを合わせて実際の引数・戻り値の型を確認し、`WandItem.java`/`SpellbookItem.java`の`use`メソッドをそれに合わせて書き換える(戻り値が `ActionResult` ではなく `TypedActionResult<ItemStack>`等の場合あり) |
| `ArcaneAltarBlock` の `onUse` が「親をオーバーライドしていない」 | `Block` クラスの `onUse` / `onUseWithItem` | 同様に実際のシグネチャを確認して合わせる。GUIを開く処理自体(`openHandledScreen`)は変更不要なはず |
| `getItemCooldownManager().isCoolingDown(stack)` 等でエラー | `ItemCooldownManager` のメソッドが `Item` 引数版のみの場合 | `isCoolingDown(stack)` → `isCoolingDown(stack.getItem())`、`set(stack, ticks)` → `set(stack.getItem(), ticks)` に書き換える |
| `ComponentType.builder().codec(...).packetCodec(...)` 周りでジェネリクスのエラー | `component/ModComponents.java` | Minecraftの`ComponentType`のビルダーAPIは版によって微妙にメソッド名が違うことがあります。`ComponentType`クラスの中身をIDEで開いて、`codec` / `packetCodec` に相当するメソッド名を確認してください |
| `new SmallFireballEntity(world, caster, x, y, z)` でエラー | `spell/spells/FireballSpell.java` | `SmallFireballEntity`のコンストラクタ引数をIDEで確認し、`Vec3d`版だったら `velocity.x, velocity.y, velocity.z` の3引数をVec3d1個にまとめる等調整 |
| `caster.requestTeleport(...)` が無い | `spell/spells/BlinkSpell.java` | `Entity`/`LivingEntity`にある実際のテレポート用メソッド(`teleport(...)`等)に差し替える |
| Mixin (`MouseMixin.java`) が `Cannot find target method` 等でクラッシュする | `Mouse`クラスの`onMouseScroll`の実際のメソッド名・引数 | IDEで`net.minecraft.client.Mouse`を開いて該当メソッド名を確認し、`@Inject(method = "...")`の文字列を修正。**それでも直らない場合は`arcanemagic.mixins.json`の`"client": ["MouseMixin"]`の行を削除してビルドしてください**。Ctrl+ホイールは使えなくなりますが、キーバインド(`,`/`.`)だけで魔導書の魔法切り替えは問題なく動作します |
| `Item.Settings#registryKey` / `useBlockPrefixedTranslationKey` が無い | `ModItems.java` / `ModBlocks.java` | それらのメソッド呼び出し部分を削除して `Registry.register(Registries.ITEM, Identifier.of(...), item)` の形に戻す |
| `FabricItemGroup` が見つからない | `item/group/ModItemGroup.java` | `fabric-api`が依存関係に正しく入っているか`build.gradle`を確認。それでもだめならバニラの `ItemGroup.create(...)` ビルダーに置き換える |

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
  UIが欲しい場合は`ArcaneAltarScreenHandler`に削除用の触媒アイテムを追加するのがおすすめです。

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
    │   ├── component/                  … 杖/魔導書のデータ保存(DataComponent)
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
