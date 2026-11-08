#!/usr/bin/env python3
"""Build the compact offline assets bundled in the keyboard APK.

Sources (all permissively licensed, see assets/licenses/NOTICE.txt):
  * rime/luna-pinyin      - curated word -> pinyin (Apache-2.0 / derived works)
  * rime/rime-essay       - word frequency list
  * mozillazg/phrase-pinyin-data - phrase -> pinyin (MIT)
  * BYVoid/OpenCC         - simplified <-> traditional dictionaries (Apache-2.0)

Outputs:
  pinyin_dict.bin   sorted pinyin-key -> word dictionary with weights
  syllables.txt     valid toneless pinyin syllables (segmentation table)
  trad_map.bin      simplified -> traditional longest-match map
"""
import os
import re
import struct
import sys
import io
import unicodedata

DATA = os.environ.get("DICT_SOURCES", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dict-sources"))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets")
CJK = re.compile(r"^[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]+$")
SYLLABLE = re.compile(r"^[a-z]{1,7}$")


def toneless(text):
    text = text.replace("ü", "v").replace("Ü", "V")
    text = unicodedata.normalize("NFD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return text.lower().strip()


def load_opencc(name):
    table = {}
    with open(f"{DATA}/opencc/data/dictionary/{name}", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#") or "\t" not in line:
                continue
            k, v = line.split("\t", 1)
            table[k] = v.split(" ")[0]
    return table


def make_converter(table):
    maxlen = max(len(k) for k in table)

    def convert(word):
        out, i, n = [], 0, len(word)
        while i < n:
            hit = None
            for ln in range(min(maxlen, n - i), 0, -1):
                seg = word[i:i + ln]
                if seg in table:
                    hit = (seg, table[seg])
                    break
            if hit:
                out.append(hit[1])
                i += len(hit[0])
            else:
                out.append(word[i])
                i += 1
        return "".join(out)

    return convert


def main():
    t2s_tab = {}
    t2s_tab.update(load_opencc("TSCharacters.txt"))
    t2s_tab.update(load_opencc("TSPhrases.txt"))
    to_simplified = make_converter(t2s_tab)

    s2t_tab = {}
    s2t_tab.update(load_opencc("STCharacters.txt"))
    s2t_tab.update(load_opencc("STPhrases.txt"))
    to_traditional = make_converter(s2t_tab)

    # ---- frequency table ------------------------------------------------
    freq = {}
    with open(f"{DATA}/essay/essay.txt", encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) == 2 and p[1].isdigit():
                freq[p[0]] = int(p[1])
    print("essay words:", len(freq))

    def weight_of(word):
        w = freq.get(word)
        if w is None:
            w = freq.get(to_traditional(word))
        if w is None:
            w = freq.get(to_simplified(word))
        return w or 0

    # ---- character readings ---------------------------------------------
    char_reads = {}  # char -> [(syllable, probability)]
    luna_rows = []
    with open(f"{DATA}/luna/luna_pinyin.dict.yaml", encoding="utf-8") as f:
        lines = f.read().split("\n")
    for line in lines[lines.index("...") + 1:]:
        if not line.strip() or line.startswith("#"):
            continue
        cols = line.split("\t")
        if len(cols) < 2:
            continue
        word, py = cols[0], cols[1]
        prob = 1.0
        if len(cols) >= 3 and cols[2].endswith("%"):
            try:
                prob = float(cols[2].rstrip("%")) / 100.0
            except ValueError:
                prob = 1.0
        syls = [toneless(s) for s in py.split()]
        if not syls or not all(SYLLABLE.match(s) for s in syls):
            continue
        luna_rows.append((word, syls, prob))
        if len(word) == 1:
            char_reads.setdefault(word, []).append((syls[0], prob))
    for c in char_reads:
        char_reads[c].sort(key=lambda x: -x[1])
    print("chars with readings:", len(char_reads))

    entries = {}

    def add(word, syls, w):
        if not word or not CJK.match(word) or not (1 <= len(word) <= 8):
            return
        if not syls or len(syls) > len(word) + 1:
            return
        key = " ".join(syls)
        old = entries.get((word, key))
        if old is None or w > old:
            entries[(word, key)] = w

    # ---- phrase data ------------------------------------------------------
    for name in ("pinyin.txt", "large_pinyin.txt"):
        with open(f"{DATA}/phrase-pinyin-data/{name}", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or ":" not in line:
                    continue
                word, py = line.split(":", 1)
                word = word.strip()
                syls = [toneless(s) for s in py.split()]
                if not syls or not all(SYLLABLE.match(s) for s in syls):
                    continue
                add(word, syls, weight_of(word))
    print("after phrase data:", len(entries))

    # ---- luna curated dictionary -----------------------------------------
    for word, syls, prob in luna_rows:
        base = weight_of(word)
        w = int(base * prob) if base else int(20 * prob)
        add(word, syls, w)
    print("after luna:", len(entries))

    # ---- preset vocabulary: essay words whose pinyin we can derive --------
    have_simp = {to_simplified(w) for w, _ in entries}
    derived = 0
    for word, w in freq.items():
        if w < 200 or not (2 <= len(word) <= 6) or not CJK.match(word):
            continue
        if to_simplified(word) in have_simp:
            continue
        syls = []
        ok = True
        for ch in word:
            reads = char_reads.get(ch) or char_reads.get(to_simplified(ch))
            if not reads:
                ok = False
                break
            syls.append(reads[0][0])
        if not ok:
            continue
        add(word, syls, w)
        have_simp.add(to_simplified(word))
        derived += 1
    print("derived from vocabulary:", derived, "total:", len(entries))

    # ---- selection --------------------------------------------------------
    norm = {}
    for (word, key), w in entries.items():
        s = to_simplified(word)
        if not CJK.match(s):
            continue
        k = (s, key)
        if w > norm.get(k, -1):
            norm[k] = w
    singles = {k: v for k, v in norm.items() if len(k[0]) == 1}
    multi = [(k, v) for k, v in norm.items() if len(k[0]) > 1]
    multi.sort(key=lambda kv: -kv[1])
    multi = [(k, v) for k, v in multi if v > 0][:160000]
    print("singles:", len(singles), "multi:", len(multi))

    rows = [(key, word, max(w, 0)) for (word, key), w in singles.items()]
    rows += [(key, word, w) for (word, key), w in multi]

    best = {}
    for key, word, w in rows:
        k = (key, word)
        if w > best.get(k, -1):
            best[k] = w
    rows = sorted(((k[0], k[1], w) for k, w in best.items()),
                  key=lambda r: (r[0], -r[2], r[1]))
    print("final entries:", len(rows))

    # ---- write dictionary -------------------------------------------------
    key_blob = bytearray()
    word_blob = bytearray()
    key_off = [0]
    word_off = [0]
    weights = []
    syllables = set()
    for key, word, w in rows:
        key_blob += key.encode("ascii")
        key_off.append(len(key_blob))
        word_blob += word.encode("utf-8")
        word_off.append(len(word_blob))
        weights.append(min(w, 2 ** 31 - 1))
        syllables.update(key.split(" "))

    syl_blob = "\n".join(sorted(syllables)).encode("ascii")
    os.makedirs(OUT, exist_ok=True)
    # The dictionary is shipped in <4 MB parts: some tooling (and the GitHub
    # contents API) refuses very large single blobs.
    buffer = io.BytesIO()
    buffer.write(b"CKPY")
    buffer.write(struct.pack("<iiiiii", 3, len(rows), 8, len(key_blob),
                             len(word_blob), len(syl_blob)))
    buffer.write(struct.pack(f"<{len(key_off)}i", *key_off))
    buffer.write(struct.pack(f"<{len(word_off)}i", *word_off))
    buffer.write(struct.pack(f"<{len(weights)}i", *weights))
    buffer.write(key_blob)
    buffer.write(word_blob)
    buffer.write(syl_blob)
    data = buffer.getvalue()
    buffer.close()
    for old in os.listdir(OUT):
        if old.startswith("pinyin_dict."):
            os.remove(os.path.join(OUT, old))
    chunk = 2_600_000
    parts = [data[i:i + chunk] for i in range(0, len(data), chunk)]
    for index, part in enumerate(parts):
        with open(f"{OUT}/pinyin_dict.{index}", "wb") as f:
            f.write(part)
    with open(f"{OUT}/syllables.txt", "wb") as f:
        f.write(syl_blob)
    print("dict size: %.2f MB in %d parts" % (len(data) / 1e6, len(parts)),
          "syllables:", len(syllables))

    # ---- traditional map --------------------------------------------------
    tmap = {}
    for name, limit in (("STCharacters.txt", 8), ("STPhrases.txt", 12)):
        with open(f"{DATA}/opencc/data/dictionary/{name}", encoding="utf-8") as f:
            for line in f:
                line = line.rstrip("\n")
                if not line or line.startswith("#") or "\t" not in line:
                    continue
                simp, trad = line.split("\t", 1)
                cand = trad.split(" ")[0].strip()
                if not simp or not cand or len(simp) > limit:
                    continue
                tmap[simp] = cand
    # Prefer the Taiwan-standard glyphs (為 not 爲, 吃 not 喫, 裡 not 裏).
    tw_tab = {}
    for name in ("TWVariants.txt", "TWVariantsPhrases.txt"):
        path = f"{DATA}/opencc/data/dictionary/{name}"
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.rstrip("\n")
                if not line or line.startswith("#") or "\t" not in line:
                    continue
                k, v = line.split("\t", 1)
                tw_tab[k] = v.split(" ")[0]
    to_tw = make_converter(tw_tab) if tw_tab else (lambda w: w)
    tmap = {k: to_tw(v) for k, v in tmap.items()}

    items = sorted(tmap.items(), key=lambda kv: kv[0].encode("utf-8"))
    kb, vb = bytearray(), bytearray()
    ko, vo = [0], [0]
    maxlen = 0
    for k, v in items:
        maxlen = max(maxlen, len(k))
        kb += k.encode("utf-8")
        ko.append(len(kb))
        vb += v.encode("utf-8")
        vo.append(len(vb))
    with open(f"{OUT}/trad_map.bin", "wb") as f:
        f.write(b"CKST")
        f.write(struct.pack("<iii", 1, len(items), maxlen))
        f.write(struct.pack(f"<{len(ko)}i", *ko))
        f.write(struct.pack(f"<{len(vo)}i", *vo))
        f.write(kb)
        f.write(vb)
    print("trad entries:", len(items), "size: %.2f MB" %
          (os.path.getsize(f"{OUT}/trad_map.bin") / 1e6))


if __name__ == "__main__":
    main()
