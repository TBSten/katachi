"""katachi のライブラリ（`:katachi` と `:katachi-konsist`）の公開宣言を、ソースの素朴な字句走査で拾う。

スクリプトではなく、ほかのスクリプトが読み込む部品。check-docs-against-impl の list-api-changes.py と、
prerelease の list-public-api.py が使う。

**目安であって、構文解析ではない。**拾い方:

- 両モジュールは explicitApi() なので、公開する宣言には必ず `public` が書いてある。`public` の付いた宣言のうち、
  囲む型もすべて `public` のものを公開宣言とする。`override` は新しい API ではないので数えない
- コメントと文字列は先に消してから数える（KDoc のコード例の `public` を拾わないため）
- 注釈は、宣言の直前の行（と同じ行の前）にある `@Foo` を拾う。型に付いた `@InternalKatachiApi` /
  `@ExperimentalKatachiApi` はメンバにも効く（Kotlin の opt-in と同じ）ので、囲む型の注釈も足して区分けする
- 1つの宣言が複数行にまたがるときは、括弧が閉じるまでをシグネチャとして読む

外れうるもの: 1行に2つの宣言を書いたもの、文字列テンプレートの中の入れ子の文字列、`{` を含む既定値。
"""
import re, subprocess
from dataclasses import dataclass, field
from pathlib import Path

MODULES = {
    ":katachi": "katachi/src/main",
    ":katachi-konsist": "katachi-konsist/src/main",
}
INTERNAL_ANNOTATION = "InternalKatachiApi"
EXPERIMENTAL_ANNOTATION = "ExperimentalKatachiApi"
CATEGORIES = ("public", "internal-api", "experimental")
CATEGORY_LABELS = {
    "public": "public（注釈なし）",
    "internal-api": "public + `@InternalKatachiApi`",
    "experimental": "public + `@ExperimentalKatachiApi`",
}
TYPE_KINDS = {"class", "interface", "object"}

_MODIFIERS = (
    "public|private|internal|protected|abstract|open|sealed|data|enum|annotation|inline|value|"
    "override|suspend|operator|infix|const|lateinit|inner|companion|expect|actual|tailrec|external|final"
)
_DECL = re.compile(
    rf"^(?P<mods>(?:(?:{_MODIFIERS})\s+)*)"
    r"(?:(?P<funiface>fun)\s+(?=interface\b))?"
    r"(?P<kind>class|interface|object|fun|val|var|typealias|constructor)\b(?P<rest>.*)$"
)
_ANNOTATION = re.compile(r"@(?:[a-z]+:)?([A-Za-z_][\w.]*)")
_LEADING_ANNOTATIONS = re.compile(r"^(?:@(?:[a-z]+:)?[A-Za-z_][\w.]*(?:\s*\((?:[^()]|\([^()]*\))*\))?\s*)+")
_INLINE_CTOR = re.compile(r"((?:@[A-Za-z_][\w.]*\s+)*)public\s+constructor\b")


@dataclass
class Decl:
    module: str
    path: str  # リポジトリからの相対パス
    line: int
    package: str
    owners: list  # 囲む型の名前（外側から）
    kind: str
    name: str
    modifiers: set
    annotations: set  # 自分に付いた注釈の単純名
    inherited: set = field(default_factory=set)  # 囲む型から効く注釈
    public: bool = True
    signature: str = ""

    @property
    def qualified(self):
        return ".".join([*self.owners, self.name])

    @property
    def category(self):
        all_annotations = self.annotations | self.inherited
        if INTERNAL_ANNOTATION in all_annotations:
            return "internal-api"
        if EXPERIMENTAL_ANNOTATION in all_annotations:
            return "experimental"
        return "public"

    @property
    def in_internal_package(self):
        return "internal" in self.package.split(".")

    @property
    def key(self):
        """基準と今とで同じ宣言を突き合わせる鍵。パッケージは入れない（移動を「移動」として拾うため）。"""
        return (self.module, self.qualified, self.kind)


def strip_comments_and_strings(text):
    """コメントと文字列の中身を空白に置き換える。改行は残すので行番号はずれない。"""
    out = []
    i, n = 0, len(text)
    # モードのスタック: "code"（テンプレートの ${ } の中では波括弧の深さを持つ）, '"', '"""'
    stack = [["code", 0]]
    while i < n:
        mode = stack[-1][0]
        c = text[i]
        if mode == "code":
            if text.startswith("//", i):
                j = text.find("\n", i)
                j = n if j < 0 else j
                out.append(" " * (j - i))
                i = j
                continue
            if text.startswith("/*", i):
                depth, j = 1, i + 2
                while j < n and depth:
                    if text.startswith("/*", j):
                        depth, j = depth + 1, j + 2
                    elif text.startswith("*/", j):
                        depth, j = depth - 1, j + 2
                    else:
                        j += 1
                out.append("".join(ch if ch == "\n" else " " for ch in text[i:j]))
                i = j
                continue
            if text.startswith('"""', i):
                stack.append(['"""', 0])
                out.append('"""')
                i += 3
                continue
            if c == '"':
                stack.append(['"', 0])
                out.append('"')
                i += 1
                continue
            if c == "'":
                j = i + 1
                while j < n and text[j] != "'" and text[j] != "\n":
                    j += 2 if text[j] == "\\" else 1
                out.append("' '")
                i = j + 1
                continue
            if len(stack) > 1:  # ${ } の中
                if c == "{":
                    stack[-1][1] += 1
                elif c == "}":
                    if stack[-1][1] == 0:
                        stack.pop()
                        out.append(" ")
                        i += 1
                        continue
                    stack[-1][1] -= 1
            out.append(c)
            i += 1
            continue
        # 文字列の中
        if mode == '"' and c == "\\":
            out.append("  ")
            i += 2
            continue
        if text.startswith("${", i):
            stack.append(["code", 0])
            out.append("  ")
            i += 2
            continue
        if (mode == '"""' and text.startswith('"""', i)) or (mode == '"' and c == '"'):
            width = 3 if mode == '"""' else 1
            # """ の閉じは、続く " をまとめて食う
            while mode == '"""' and text.startswith('"', i + width):
                width += 1
            stack.pop()
            out.append('"' * width)
            i += width
            continue
        out.append("\n" if c == "\n" else " ")
        i += 1
    return "".join(out)


def _name_of(kind, rest, owner):
    rest = rest.strip()
    if kind == "constructor":
        return "<init>"
    if kind == "object" and (not rest or rest[0] in "{:"):
        return "Companion"
    if rest.startswith("<"):  # 型引数
        depth = 0
        for j, ch in enumerate(rest):
            depth += ch == "<"
            depth -= ch == ">"
            if depth == 0:
                rest = rest[j + 1 :].strip()
                break
    # 拡張の受け手を飛ばし、名前の直前の `.` のあとを取る（`Foo<Bar>.name(` / `List<X>.name:`）
    m = re.match(r"^((?:[^\s(:=<{]|<[^>]*>)*)", rest)
    head = m.group(1) if m else rest
    if head.startswith("`"):
        return head.strip("`")
    name = re.split(r"\.(?![^<]*>)", head)[-1].strip("`")
    return re.sub(r"<.*$", "", name) or "?"  # `class Foo<T>` の型引数


def scan_source(text, module, path):
    """1ファイルの公開宣言（と、公開でない型の宣言も含む全宣言）を返す。"""
    code = strip_comments_and_strings(text)
    lines = code.split("\n")
    package = ""
    decls = []
    stack = []  # ("type", Decl) | ("other", None)
    pending_type = None  # 本体の { をまだ開いていない型
    pending_annotations = set()
    paren = 0
    capturing = []  # シグネチャを読み足している宣言と、その行の頭の括弧の深さ

    for no, raw in enumerate(lines, 1):
        s = raw.strip()
        for d, _ in capturing:
            if d.line != no:
                d.signature += " " + s
        if s.startswith("package "):
            package = s.split()[1]
        header_param = pending_type is not None and paren > 0
        at_member_level = paren == 0 and (not stack or stack[-1][0] == "type")
        m_anno = _LEADING_ANNOTATIONS.match(s)
        rest = s[m_anno.end():].strip() if m_anno else s
        if m_anno:
            pending_annotations |= set(a.split(".")[-1] for a in _ANNOTATION.findall(m_anno.group(0)))
        m = _DECL.match(rest) if rest and not rest.startswith("context(") else None
        if m and not (header_param and m.group("kind") not in ("val", "var")):
            kind = "interface" if m.group("funiface") else m.group("kind")
            if header_param or at_member_level:
                if at_member_level and not header_param:
                    pending_type = None
                owner_decls = [d for k, d in stack if k == "type"]
                if header_param:
                    owner_decls = owner_decls + [pending_type]
                mods = set(m.group("mods").split())
                decl = Decl(
                    module=module, path=path, line=no, package=package,
                    owners=[d.name for d in owner_decls], kind=kind,
                    name=_name_of(kind, m.group("rest"), owner_decls[-1] if owner_decls else None),
                    modifiers=mods, annotations=pending_annotations,
                    inherited=set().union(*[d.annotations | d.inherited for d in owner_decls]) if owner_decls else set(),
                    public="public" in mods and all(d.public for d in owner_decls) and "override" not in mods,
                    signature=rest,
                )
                decls.append(decl)
                capturing.append((decl, paren))
                if kind in TYPE_KINDS:
                    pending_type = decl
                    # `class Foo @Ann public constructor(` の主コンストラクタ
                    ctor = _INLINE_CTOR.search(m.group("rest"))
                    if ctor:
                        decls.append(Decl(
                            module=module, path=path, line=no, package=package,
                            owners=decl.owners + [decl.name], kind="constructor", name="<init>",
                            modifiers={"public"},
                            annotations=set(a.split(".")[-1] for a in _ANNOTATION.findall(ctor.group(1))),
                            inherited=decl.annotations | decl.inherited,
                            public=decl.public, signature=rest,
                        ))
            pending_annotations = set()
        elif s and not m_anno and not s.startswith("context(") and paren == 0:
            pending_annotations = set()

        for ch in raw:
            if ch == "(":
                paren += 1
            elif ch == ")":
                paren = max(0, paren - 1)
            elif ch == "{":
                if pending_type is not None and paren == 0:
                    stack.append(("type", pending_type))
                    pending_type = None
                else:
                    stack.append(("other", None))
            elif ch == "}":
                if stack:
                    stack.pop()
        done = [(d, start) for d, start in capturing if paren <= start]
        for d, _ in done:
            d.signature = _tidy_signature(d.signature)
        capturing = [c for c in capturing if c not in done]
    return decls


def _tidy_signature(sig):
    sig = re.sub(r"\s+", " ", sig).strip()
    # 本体と初期化子は落とす（括弧の外の最初の `{` / ` = `）
    depth = 0
    for j, ch in enumerate(sig):
        if ch in "(<":
            depth += 1
        elif ch in ")>":
            depth = max(0, depth - 1)
        elif depth == 0 and (ch == "{" or sig.startswith(" = ", j)):
            return sig[:j].strip()
    return sig


def repo_root():
    out = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True)
    return Path(out.stdout.strip() or ".").resolve()


def scan_worktree(root):
    decls = []
    for module, src in MODULES.items():
        for f in sorted((root / src).rglob("*.kt")):
            rel = f.relative_to(root).as_posix()
            decls += scan_source(f.read_text(encoding="utf-8"), module, rel)
    return decls


def scan_ref(root, ref):
    """git の ref の時点のソースを走査する。ref が無ければ None。"""
    ok = subprocess.run(["git", "rev-parse", "--verify", "-q", f"{ref}^{{commit}}"], cwd=root,
                        capture_output=True, text=True)
    if ok.returncode != 0:
        return None
    decls = []
    for module, src in MODULES.items():
        names = subprocess.run(["git", "ls-tree", "-r", "--name-only", ref, "--", src], cwd=root,
                               capture_output=True, text=True).stdout.split()
        names = [p for p in names if p.endswith(".kt")]
        if not names:
            continue
        batch = subprocess.run(["git", "cat-file", "--batch"], cwd=root, capture_output=True,
                               input="\n".join(f"{ref}:{p}" for p in names).encode() + b"\n").stdout
        pos = 0
        for p in names:
            header_end = batch.index(b"\n", pos)
            size = int(batch[pos:header_end].split()[2])
            body = batch[header_end + 1 : header_end + 1 + size].decode("utf-8", "replace")
            pos = header_end + 1 + size + 1
            decls += scan_source(body, module, p)
    return decls


def public_only(decls):
    return [d for d in decls if d.public]


def latest_release_tag(root):
    """`vX.Y.Z` の形のタグのうち、版の番号がいちばん大きいもの。"""
    tags = subprocess.run(["git", "tag", "-l", "v*"], cwd=root, capture_output=True, text=True).stdout.split()
    versions = [(parse_version(t[1:]), t) for t in tags if re.fullmatch(r"v\d+\.\d+\.\d+(?:[-+].*)?", t)]
    return max(versions)[1] if versions else None


def parse_version(v):
    core = re.split(r"[-+]", v, 1)[0]
    return tuple(int(x) for x in core.split("."))


def compare(current, base):
    """(今の宣言 → 状態, 基準にだけある宣言のリスト)。状態は 追加 / 変更 / 移動 / 区分変更 / None。"""
    if base is None:
        return {}, []
    base_by_key = {}
    for d in base:
        base_by_key.setdefault(d.key, []).append(d)
    cur_keys = {d.key for d in current}
    status = {}
    for d in current:
        olds = base_by_key.get(d.key)
        if not olds:
            status[id(d)] = "追加"
            continue
        marks = []
        if d.package not in {o.package for o in olds}:
            marks.append("移動")
        if d.category not in {o.category for o in olds}:
            marks.append("区分変更")
        if d.signature not in {o.signature for o in olds}:
            marks.append("変更")
        status[id(d)] = "・".join(marks) or None
    removed = [d for d in base if d.key not in cur_keys]
    return status, removed


def file_uri(root, path, line=None):
    uri = (root / path).resolve().as_uri()
    return f"{uri}:{line}" if line else uri


# ---- ドキュメントの側 ----

# 確かめる対象のドキュメント（日本語の原本だけ。英語は translate-ja-en で追随させる）。SKILL.md の「対象」の表と合わせる
DOC_GLOBS = (
    "docs/src/content/docs/ja/**/*.mdx",
    "docs/src/content/docs/ja/**/*.md",
    "README.ja.md",
    "sample/**/README.md",
    "docs/public/install/ja/index.md",
)
_SKIP_DIRS = {"build", "node_modules", ".gradle", ".kotlin", ".idea"}


def doc_files(root):
    found = set()
    for pattern in DOC_GLOBS:
        for f in root.glob(pattern):
            rel = f.relative_to(root)
            if f.is_file() and not (_SKIP_DIRS & set(rel.parts)):
                found.add(rel.as_posix())
    return sorted(found)


def read_lines(root, path):
    return (root / path).read_text(encoding="utf-8").split("\n")


# ---- Gradle プラグイン（Java）の側 ----

GRADLE_PLUGIN_SRC = "katachi-gradle-plugin/src/main/java"
_JAVA_PUBLIC = re.compile(
    r"^\s*public\s+(?:(?:abstract|static|final|default|synchronized)\s+)*"
    r"(?:(?P<type>class|interface|enum|record)\s+(?P<tname>\w+)|(?:<[^>]*>\s*)?[\w.<>\[\], ?]+?\s+(?P<mname>\w+)\s*\()"
)


def scan_java(text, path):
    """Gradle プラグインの public な型とメソッド（利用者が build.gradle.kts に書く DSL の名前）。(型, 名前) の集合。"""
    owner = Path(path).stem
    names = set()
    for line in strip_comments_and_strings(text).split("\n"):
        m = _JAVA_PUBLIC.match(line)
        if m:
            names.add((owner, m.group("tname") or m.group("mname")))
    return names


def java_api_worktree(root):
    out = {}
    for f in sorted((root / GRADLE_PLUGIN_SRC).rglob("*.java")):
        rel = f.relative_to(root).as_posix()
        for key in scan_java(f.read_text(encoding="utf-8"), rel):
            out[key] = rel
    return out


def java_api_ref(root, ref):
    names = subprocess.run(["git", "ls-tree", "-r", "--name-only", ref, "--", GRADLE_PLUGIN_SRC], cwd=root,
                           capture_output=True, text=True).stdout.split()
    out = {}
    for p in names:
        if p.endswith(".java"):
            text = subprocess.run(["git", "show", f"{ref}:{p}"], cwd=root, capture_output=True, text=True).stdout
            for key in scan_java(text, p):
                out[key] = p
    return out
