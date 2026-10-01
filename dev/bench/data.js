window.BENCHMARK_DATA = {
  "lastUpdate": 1790892750341,
  "repoUrl": "https://github.com/TBSten/katachi",
  "entries": {
    "JMH time": [
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "2c41ea67c4a43d81f73c5b84ffb8c76ac43c4ca1",
          "message": "ci(ide-plugin): verifyPreview の「is cut」に寸法を添え、失敗時にプレビューの PNG を全部残す\n\nCI の macOS で初めて ide-plugin ジョブが動き、ダイアログのプレビューのゲートが「生成 is cut」で\n76 件落ちた。手元の macOS では JVM の言語を変えても通るので、runner のフォントの寸法の差と見ているが、\nCI で描かれた PNG が無く確かめられない。失敗の文言にテキストと枠の寸法・行数・フォントを出し、\n失敗時の成果物に build/preview を丸ごと入れる（ゲートで落ちると before/after の report は作られない）。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T08:26:05+09:00",
          "tree_id": "5f111c582a324c006d5b8c607dc182c41a885cab",
          "url": "https://github.com/TBSten/katachi/commit/2c41ea67c4a43d81f73c5b84ffb8c76ac43c4ca1"
        },
        "date": 1790811707911,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.217214141885053,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 43.0575542893617,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.396154065754926,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 49.11648960801104,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1353.5699247185362,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4458.259060688788,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12454.338922399595,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41611.93730255102,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1260.5174481977513,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4168.191905651452,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12297.563870760536,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 40964.719323795915,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 35.370311152878784,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 96.06996192380952,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 469.8589381999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1825.0232619499996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.777616630349505,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.001770380292048,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18.80078791601226,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 57.93300064857142,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.831676640740993,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14.512037620036612,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27.396728113195525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 67.80699640482759,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 150.8339296,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 216.94505279999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 838.0665422,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1520.4864684,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "4cf97bc9e44b8b0b222649796215613d84010902",
          "message": "test(architecture): IDE プラグインのプレビューのフォントと、それを作り直すスクリプトの Role を足す\n\n8691d5c で足した src/preview/resources/fonts と scripts/preview-font が、リポジトリ自身の定義で\nUnexpectedDirectory になっていた。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T10:08:38+09:00",
          "tree_id": "2bfd180496f71f04e18ab83def6d1d18b3760cf7",
          "url": "https://github.com/TBSten/katachi/commit/4cf97bc9e44b8b0b222649796215613d84010902"
        },
        "date": 1790817867913,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.240640202913356,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 43.00689823829788,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.271479324247549,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 45.49529163561071,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1356.986170165945,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4212.744473072465,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12354.062456629077,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41452.886725000004,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1253.9056410611804,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4206.140836712189,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12520.732951713164,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41935.68233951278,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 33.7833594162917,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 103.32660746917293,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 465.73143088000006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1771.1849880500001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.7501868938745817,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 6.8269657447352134,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18.368426740867697,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56.859281861904776,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.497821873083966,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14.224007683638641,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 26.858689384307855,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 68.12128337402298,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 153.7408074,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 226.09643580000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 870.75299,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1595.0723830000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "0975c842a0b5ea52fef6dd818adaea8be0f7c5c4",
          "message": "fix(ide-plugin): verifyPreview の golden との比較で、文字の縁の階調ほどの差は同じとみなす\n\nバイトで比べていたため、別の macOS（GitHub の runner）で描くと全 PNG が changed になった。\nCI で描いた 304 枚と golden の差は、最大 47 ピクセル・各チャンネル 9/255 で、どれも文字の縁の\n階調だけ。バイトが違うときは画像として読み、各チャンネルの差が 16/255 以内なら同じとみなす。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T10:33:22+09:00",
          "tree_id": "2b0e0bfed838e596c01d9af0b30054768fd5fa67",
          "url": "https://github.com/TBSten/katachi/commit/0975c842a0b5ea52fef6dd818adaea8be0f7c5c4"
        },
        "date": 1790819331511,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.233894465242951,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 42.835172255319144,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.248483157334624,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 45.789800180873854,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1355.4433445611244,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4406.096102054844,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12460.52117199924,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 40718.35798110204,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1270.4659121909206,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4137.961190636229,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12191.48780301984,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 40566.0059307347,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 37.0943429029293,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 95.80417890324676,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 450.16016112000005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1813.90083505,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.737218037371867,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.086308450232958,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18.3816517145955,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 57.547939382857145,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.769922040494983,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14.06221516917328,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27.241286038655574,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 67.06195566967742,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 148.6796276,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 210.4117342,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 818.1157192,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1532.6207376,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "c7ddaa40a06a32138e357f49279ce18ad96893b8",
          "message": "fix(ide-plugin): 確認用の出力先を手元のパスで決め打ちしていたテストを直し、integrationTest の補助ファイルを Role に入れる\n\nMultiTemplateRenderTest・MultiTemplateNewMenuTest・MultiTemplateInjectionTest は、PNG やダンプの\n既定の出力先（と、読むリポジトリ）を /Users/tbsten/dev/katachi/.local/... にしていたため、CI の\nrunner で FileNotFoundException になった。出力先は build/verify-multi-template、読むリポジトリは\nこのビルドの親にする。a98ea7d で足した integrationTest の RepositoryCopy.kt が、リポジトリ自身の\n定義で UnexpectedFile になっていたので、IdePluginTest の integrationTest を *Test 以外も含む形にする。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T10:50:59+09:00",
          "tree_id": "58db57e336b0854a40e9e92729eea3c95a9ab98b",
          "url": "https://github.com/TBSten/katachi/commit/c7ddaa40a06a32138e357f49279ce18ad96893b8"
        },
        "date": 1790820389872,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.900429044109208,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 49.16986723583043,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 8.327732801502075,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 55.75453131021021,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1340.0315538886866,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4261.6074848016,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12762.885259316621,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41275.990520408166,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1234.5569288930142,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4133.952875387873,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12397.59509849219,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 40691.10102521289,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 41.14555543465475,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 135.39363062875003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 331.2904453761905,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1835.95282935,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.791019918097246,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.281454579050854,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 19.828419156660452,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 59.95094307627594,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 10.271566486176479,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14.82166143311726,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 28.3411345898083,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 71.58754738054188,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 141.69665700000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 204.91223599999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 821.074828,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1442.3704282000003,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "0a32e004e0cf820cf8d530b93df43d95511e8bab",
          "message": "docs: ガイドと手順書の例から .module { } と modulePackage を外し、廃止予定の書き方を例に使わない方針を書く\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T18:01:23+09:00",
          "tree_id": "afc228aa47a4b83ce31f1201e0b3f09ba7ad2ce3",
          "url": "https://github.com/TBSten/katachi/commit/0a32e004e0cf820cf8d530b93df43d95511e8bab"
        },
        "date": 1790860802796,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.2491416955347985,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 43.30725360938945,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.282060489585947,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 48.24925867935742,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1356.9105195749894,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4329.188679112793,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12491.006967309857,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41545.16090263775,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1263.114322292518,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4271.840040516551,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12275.432834564446,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41257.35942857142,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 37.1206565906734,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 112.23422952837977,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 462.55490954000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1845.5163699499997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.744759676448912,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 6.915080038284165,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18.858251817112734,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 57.776597546031745,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.56102288618948,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 13.92008568881225,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 26.966680317063066,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 69.0056218821839,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 146.9494574,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 216.3635854,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 841.6231706000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1535.0543614000003,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "398d5f35b1be8c390d61f358476597cfb2d39f84",
          "message": "ci: 実プロジェクトのベンチマークのぶれの幅を測る、手動起動のワークフローを足す\n\n同じコミットを、複数の runner で並行に、各 runner で JVM を立ち上げ直して数回測り、\nrunner 間と同じ runner の中のぶれ、続けて2回走らせたときの比の最悪値を要約に出す。\nbench-store の閾値（直前の1回と比べて 150%）を決める材料にする。gh-pages には書かず、\nコメントも付けない。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T22:26:35+09:00",
          "tree_id": "f36e1db23012814b6684bf3e506c6057e6b63370",
          "url": "https://github.com/TBSten/katachi/commit/398d5f35b1be8c390d61f358476597cfb2d39f84"
        },
        "date": 1790862154565,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.285724020560753,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 43.25754854944496,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.317180764085367,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 47.32146876805094,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1411.68944390963,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4595.252026053009,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12928.268461596868,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 42911.905227659576,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1323.4994043981278,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4439.779880550986,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13223.290760741322,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 42212.378468920964,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 37.12562831639731,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 113.08533696842105,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 476.15956270000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1776.23642075,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.793092055576323,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.136767779519848,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 19.68005335962714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 60.40080150677362,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 10.107397951140399,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14.208260164097428,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27.637646260841684,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 69.41427463609196,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 149.8318678,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 207.238134,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 856.9249926,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1562.0720722,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "f95625974981b903392fe160f35f313c74a5e7b4",
          "message": "docs(install): 3-3 の .gitignore の確かめ方を、無視された定義ファイルを実際に挙げるコマンドに直す\n\ngit check-ignore に -r は無く rc=129 で落ち、-r を外すとディレクトリしか判定しないので、\n無視された定義ファイルがあっても黙って通っていた。git ls-files --others --ignored\n--exclude-standard architecture-test/src に直す（無視されたファイルがあれば一覧に出る）。\nあわせて、6-D の「message に注釈名は出ない」を 40f0c2d 以降の message に合わせ、3-3 の\nチェックリストから廃止予定の modulePackage の例を外す。手順書の通し（prerelease 9-2 の再実行）で見つかった。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-02T05:57:00+09:00",
          "tree_id": "77126e5c6b0012a5e13f2aa6a58b1d3d5ef25219",
          "url": "https://github.com/TBSten/katachi/commit/f95625974981b903392fe160f35f313c74a5e7b4"
        },
        "date": 1790892747604,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.270663138105383,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 43.72683388524514,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.423801698028276,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 47.55300221553911,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1371.6739401049222,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4413.058852248481,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12831.806239777461,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 42614.8917,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1285.6106028255779,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4338.298676732188,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12594.689640876473,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41091.05613718367,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 37.72730934031339,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 97.26912109999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 452.94590781999995,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1804.1167870999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.8038206360424636,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.043704702953804,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18.958740759121554,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 59.445461994201686,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.658610873800523,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14.413616407887087,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27.385828490183165,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 68.92583302528735,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 148.55205099999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 216.91426380000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 840.027333,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1545.0332282000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      }
    ],
    "JMH alloc/op": [
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "2c41ea67c4a43d81f73c5b84ffb8c76ac43c4ca1",
          "message": "ci(ide-plugin): verifyPreview の「is cut」に寸法を添え、失敗時にプレビューの PNG を全部残す\n\nCI の macOS で初めて ide-plugin ジョブが動き、ダイアログのプレビューのゲートが「生成 is cut」で\n76 件落ちた。手元の macOS では JVM の言語を変えても通るので、runner のフォントの寸法の差と見ているが、\nCI で描かれた PNG が無く確かめられない。失敗の文言にテキストと枠の寸法・行数・フォントを出し、\n失敗時の成果物に build/preview を丸ごと入れる（ゲートで落ちると before/after の report は作られない）。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T08:26:05+09:00",
          "tree_id": "5f111c582a324c006d5b8c607dc182c41a885cab",
          "url": "https://github.com/TBSten/katachi/commit/2c41ea67c4a43d81f73c5b84ffb8c76ac43c4ca1"
        },
        "date": 1790811709773,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4947311.529170608,
            "range": "± 383",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52144787.09787235,
            "range": "± 25528",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4508532.616965655,
            "range": "± 6109",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 20330791.330476187,
            "range": "± 108459",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1837416.8728292554,
            "range": "± 1186",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5970391.986252621,
            "range": "± 62",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17312870.215760604,
            "range": "± 176009",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 57050517.82142856,
            "range": "± 649539",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1719306.5471782717,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5747991.218986531,
            "range": "± 96378",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 16953230.19200134,
            "range": "± 172707",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56536608.44408164,
            "range": "± 865506",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656823.52848485,
            "range": "± 8",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381211.27619046,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751400.96,
            "range": "± 12",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987748,
            "range": "± 25",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4178830.551406478,
            "range": "± 19109",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10083441.773084339,
            "range": "± 62183",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27142706.374710836,
            "range": "± 154536",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 79690118.2857143,
            "range": "± 142139",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16156032.187249187,
            "range": "± 16998",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 21641139.64167411,
            "range": "± 187653",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 39553973.81401818,
            "range": "± 16522",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 93812039.54022989,
            "range": "± 1321178",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17567688,
            "range": "± 51484",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31774379.2,
            "range": "± 376870",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69714564.8,
            "range": "± 75272",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174029596.8,
            "range": "± 163212",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "4cf97bc9e44b8b0b222649796215613d84010902",
          "message": "test(architecture): IDE プラグインのプレビューのフォントと、それを作り直すスクリプトの Role を足す\n\n8691d5c で足した src/preview/resources/fonts と scripts/preview-font が、リポジトリ自身の定義で\nUnexpectedDirectory になっていた。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T10:08:38+09:00",
          "tree_id": "2bfd180496f71f04e18ab83def6d1d18b3760cf7",
          "url": "https://github.com/TBSten/katachi/commit/4cf97bc9e44b8b0b222649796215613d84010902"
        },
        "date": 1790817869853,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4945168.24727008,
            "range": "± 3438",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52122980.56170214,
            "range": "± 13633",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4517643.917266594,
            "range": "± 22104",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 20104688.696685012,
            "range": "± 242270",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1839311.3310238912,
            "range": "± 9139",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5844379.117175452,
            "range": "± 702",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17292953.32513785,
            "range": "± 199033",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 57093750.876789115,
            "range": "± 565739",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1723850.5926598192,
            "range": "± 7241",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5750841.58404414,
            "range": "± 92856",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17056848.30860486,
            "range": "± 42019",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56729945.640924886,
            "range": "± 556518",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656819.75252458,
            "range": "± 17",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381225.9528822,
            "range": "± 24",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751404.8,
            "range": "± 10",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987740.4,
            "range": "± 21",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4159166.1986752627,
            "range": "± 478",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10045284.97867078,
            "range": "± 1372",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27045930.971043773,
            "range": "± 531",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 80072412.83746031,
            "range": "± 4472",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16156294.814088464,
            "range": "± 13758",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 21470981.678045593,
            "range": "± 2322",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 39560192.9195703,
            "range": "± 6696",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 93897824.28413792,
            "range": "± 1107217",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17560761.6,
            "range": "± 61415",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31840116.8,
            "range": "± 144623",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69720344,
            "range": "± 54099",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174041265.6,
            "range": "± 170833",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "0975c842a0b5ea52fef6dd818adaea8be0f7c5c4",
          "message": "fix(ide-plugin): verifyPreview の golden との比較で、文字の縁の階調ほどの差は同じとみなす\n\nバイトで比べていたため、別の macOS（GitHub の runner）で描くと全 PNG が changed になった。\nCI で描いた 304 枚と golden の差は、最大 47 ピクセル・各チャンネル 9/255 で、どれも文字の縁の\n階調だけ。バイトが違うときは画像として読み、各チャンネルの差が 16/255 以内なら同じとみなす。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T10:33:22+09:00",
          "tree_id": "2b0e0bfed838e596c01d9af0b30054768fd5fa67",
          "url": "https://github.com/TBSten/katachi/commit/0975c842a0b5ea52fef6dd818adaea8be0f7c5c4"
        },
        "date": 1790819333920,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4946591.598763469,
            "range": "± 1210",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52130532.56170213,
            "range": "± 8579",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4509760.068406661,
            "range": "± 2957",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 20273959.641155746,
            "range": "± 35917",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1841603.486526997,
            "range": "± 5487",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5969599.4357193755,
            "range": "± 1227",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17416907.10744263,
            "range": "± 10070",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56744650.21224489,
            "range": "± 527",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1723178.579197675,
            "range": "± 15006",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5766096.379149947,
            "range": "± 68544",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 16920661.650142208,
            "range": "± 74780",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56256641.361306116,
            "range": "± 44518",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656829.85050504,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381205.0545455,
            "range": "± 10",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751401.6,
            "range": "± 10",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987740,
            "range": "± 26",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4158226.6886867383,
            "range": "± 1416",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10083446.387474628,
            "range": "± 48704",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27240163.71863219,
            "range": "± 932",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 79980372.04571429,
            "range": "± 320252",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16057541.139628321,
            "range": "± 182573",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 21758716.059475366,
            "range": "± 2215",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 39713729.12464728,
            "range": "± 271696",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 94121592.28989246,
            "range": "± 827678",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17567584,
            "range": "± 46144",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31824889.6,
            "range": "± 146196",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69712526.4,
            "range": "± 7907",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174027569.6,
            "range": "± 175643",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "c7ddaa40a06a32138e357f49279ce18ad96893b8",
          "message": "fix(ide-plugin): 確認用の出力先を手元のパスで決め打ちしていたテストを直し、integrationTest の補助ファイルを Role に入れる\n\nMultiTemplateRenderTest・MultiTemplateNewMenuTest・MultiTemplateInjectionTest は、PNG やダンプの\n既定の出力先（と、読むリポジトリ）を /Users/tbsten/dev/katachi/.local/... にしていたため、CI の\nrunner で FileNotFoundException になった。出力先は build/verify-multi-template、読むリポジトリは\nこのビルドの親にする。a98ea7d で足した integrationTest の RepositoryCopy.kt が、リポジトリ自身の\n定義で UnexpectedFile になっていたので、IdePluginTest の integrationTest を *Test 以外も含む形にする。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T10:50:59+09:00",
          "tree_id": "58db57e336b0854a40e9e92729eea3c95a9ab98b",
          "url": "https://github.com/TBSten/katachi/commit/c7ddaa40a06a32138e357f49279ce18ad96893b8"
        },
        "date": 1790820391782,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4948754.203929892,
            "range": "± 2310",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52116640.592799075,
            "range": "± 9561",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4523990.105065146,
            "range": "± 12097",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 20266865.61141141,
            "range": "± 52275",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1842470.0536083516,
            "range": "± 4105",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5912376.599940268,
            "range": "± 60474",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17417832.235718817,
            "range": "± 90",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 57072661.42040817,
            "range": "± 532068",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1723850.516573675,
            "range": "± 7242",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5750842.7971195895,
            "range": "± 92853",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17083314.81801368,
            "range": "± 144",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56355872.25019689,
            "range": "± 37756",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656841.17393199,
            "range": "± 4",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381315.5280953,
            "range": "± 27",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751135.9428573,
            "range": "± 114",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987738.4,
            "range": "± 20",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4175030.849579784,
            "range": "± 9212",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10057988.632925155,
            "range": "± 20513",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27045557.293693654,
            "range": "± 943",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 79962203.77103132,
            "range": "± 697672",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16146786.67815508,
            "range": "± 4175",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 21757574.46154057,
            "range": "± 2778",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 39556319.32816901,
            "range": "± 2305",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 93615597.05221674,
            "range": "± 42629",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17568355.2,
            "range": "± 47972",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31866409.6,
            "range": "± 9042",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69718667.2,
            "range": "± 55092",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174005427.2,
            "range": "± 480352",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "0a32e004e0cf820cf8d530b93df43d95511e8bab",
          "message": "docs: ガイドと手順書の例から .module { } と modulePackage を外し、廃止予定の書き方を例に使わない方針を書く\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T18:01:23+09:00",
          "tree_id": "afc228aa47a4b83ce31f1201e0b3f09ba7ad2ce3",
          "url": "https://github.com/TBSten/katachi/commit/0a32e004e0cf820cf8d530b93df43d95511e8bab"
        },
        "date": 1790860805109,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4947295.343178536,
            "range": "± 2300",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52131763.47678076,
            "range": "± 26524",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4531251.1659365175,
            "range": "± 9644",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 20104203.287794076,
            "range": "± 218268",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1842707.9306805138,
            "range": "± 7237",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5907816.048169707,
            "range": "± 101797",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17247951.52931399,
            "range": "± 81276",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 57099602.45568707,
            "range": "± 567332",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1717634.572810294,
            "range": "± 1147",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5806262.752963127,
            "range": "± 4556",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 16978528.93017421,
            "range": "± 166993",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56645474.824489795,
            "range": "± 421937",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656829.33279462,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381257.1383557,
            "range": "± 28",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751405.44,
            "range": "± 11",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987743.6,
            "range": "± 44",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4164165.114800674,
            "range": "± 8084",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10095146.278095443,
            "range": "± 84227",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27078780.921682376,
            "range": "± 53189",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 80064719.52761905,
            "range": "± 532738",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16155147.942873787,
            "range": "± 23521",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 21775131.169903174,
            "range": "± 18668",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 39543611.88684684,
            "range": "± 2142",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 93822180.66574714,
            "range": "± 262",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17552270.4,
            "range": "± 63709",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31825553.6,
            "range": "± 149417",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69715574.4,
            "range": "± 61464",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174028289.6,
            "range": "± 178809",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "398d5f35b1be8c390d61f358476597cfb2d39f84",
          "message": "ci: 実プロジェクトのベンチマークのぶれの幅を測る、手動起動のワークフローを足す\n\n同じコミットを、複数の runner で並行に、各 runner で JVM を立ち上げ直して数回測り、\nrunner 間と同じ runner の中のぶれ、続けて2回走らせたときの比の最悪値を要約に出す。\nbench-store の閾値（直前の1回と比べて 150%）を決める材料にする。gh-pages には書かず、\nコメントも付けない。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T22:26:35+09:00",
          "tree_id": "f36e1db23012814b6684bf3e506c6057e6b63370",
          "url": "https://github.com/TBSten/katachi/commit/398d5f35b1be8c390d61f358476597cfb2d39f84"
        },
        "date": 1790862157282,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4944448.542710936,
            "range": "± 2302",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52122981.24292323,
            "range": "± 13633",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4511851.237774564,
            "range": "± 29501",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 20281620.313178293,
            "range": "± 46024",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1841952.5209380356,
            "range": "± 8451",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5970276.959131958,
            "range": "± 2259",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17249562.941500448,
            "range": "± 268255",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 57045376.91914894,
            "range": "± 642880",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1723026.7342767906,
            "range": "± 8555",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5806576.346448391,
            "range": "± 4035",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17021959.798863105,
            "range": "± 97815",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56187332.49830655,
            "range": "± 308253",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656829.1321212,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381259.4229102,
            "range": "± 29",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751404.9599998,
            "range": "± 8",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987741.6,
            "range": "± 40",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4164194.6189406514,
            "range": "± 8000",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10069467.460119883,
            "range": "± 25052",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27187307.231357574,
            "range": "± 224922",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 80072381.32406417,
            "range": "± 3605",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16158389.760324264,
            "range": "± 16096",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 21755338.487401273,
            "range": "± 5684",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 39456200.95094324,
            "range": "± 138397",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 93519696.46252874,
            "range": "± 173897",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17572776,
            "range": "± 35520",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31824337.6,
            "range": "± 148061",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69719916.8,
            "range": "± 57657",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174059955.2,
            "range": "± 138376",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "f95625974981b903392fe160f35f313c74a5e7b4",
          "message": "docs(install): 3-3 の .gitignore の確かめ方を、無視された定義ファイルを実際に挙げるコマンドに直す\n\ngit check-ignore に -r は無く rc=129 で落ち、-r を外すとディレクトリしか判定しないので、\n無視された定義ファイルがあっても黙って通っていた。git ls-files --others --ignored\n--exclude-standard architecture-test/src に直す（無視されたファイルがあれば一覧に出る）。\nあわせて、6-D の「message に注釈名は出ない」を 40f0c2d 以降の message に合わせ、3-3 の\nチェックリストから廃止予定の modulePackage の例を外す。手順書の通し（prerelease 9-2 の再実行）で見つかった。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-02T05:57:00+09:00",
          "tree_id": "77126e5c6b0012a5e13f2aa6a58b1d3d5ef25219",
          "url": "https://github.com/TBSten/katachi/commit/f95625974981b903392fe160f35f313c74a5e7b4"
        },
        "date": 1790892749819,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4945872.3473854,
            "range": "± 385",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52101475.45124884,
            "range": "± 9166",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4515674.622455639,
            "range": "± 32909",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 20287995.782965876,
            "range": "± 81758",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1838163.9702315356,
            "range": "± 17",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5904629.160209841,
            "range": "± 104774",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17410623.932464324,
            "range": "± 11500",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56903229.34361702,
            "range": "± 878406",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1724382.6551752791,
            "range": "± 10716",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5809083.840841882,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17083278.027151503,
            "range": "± 329",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56087018.718040824,
            "range": "± 225388",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656831.38858247,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381211.6571428,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751408.16,
            "range": "± 8",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987740,
            "range": "± 21",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4159065.2753426298,
            "range": "± 445",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10048853.675975012,
            "range": "± 7495",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27045364.470327955,
            "range": "± 1415",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 79926560.19563024,
            "range": "± 232960",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16154378.901722556,
            "range": "± 20228",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 21647264.676816426,
            "range": "± 177946",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 39643750.33160146,
            "range": "± 782",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 93285726.2170115,
            "range": "± 482664",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17559907.2,
            "range": "± 63341",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31824910.4,
            "range": "± 145870",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69720564.8,
            "range": "± 64551",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174027484.8,
            "range": "± 181738",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      }
    ],
    "Real project (nowinandroid)": [
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "2c41ea67c4a43d81f73c5b84ffb8c76ac43c4ca1",
          "message": "ci(ide-plugin): verifyPreview の「is cut」に寸法を添え、失敗時にプレビューの PNG を全部残す\n\nCI の macOS で初めて ide-plugin ジョブが動き、ダイアログのプレビューのゲートが「生成 is cut」で\n76 件落ちた。手元の macOS では JVM の言語を変えても通るので、runner のフォントの寸法の差と見ているが、\nCI で描かれた PNG が無く確かめられない。失敗の文言にテキストと枠の寸法・行数・フォントを出し、\n失敗時の成果物に build/preview を丸ごと入れる（ゲートで落ちると before/after の report は作られない）。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T08:26:05+09:00",
          "tree_id": "5f111c582a324c006d5b8c607dc182c41a885cab",
          "url": "https://github.com/TBSten/katachi/commit/2c41ea67c4a43d81f73c5b84ffb8c76ac43c4ca1"
        },
        "date": 1790811711439,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 326.791072,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 59.163387,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1321.537555,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 59.597267,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "4cf97bc9e44b8b0b222649796215613d84010902",
          "message": "test(architecture): IDE プラグインのプレビューのフォントと、それを作り直すスクリプトの Role を足す\n\n8691d5c で足した src/preview/resources/fonts と scripts/preview-font が、リポジトリ自身の定義で\nUnexpectedDirectory になっていた。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T10:08:38+09:00",
          "tree_id": "2bfd180496f71f04e18ab83def6d1d18b3760cf7",
          "url": "https://github.com/TBSten/katachi/commit/4cf97bc9e44b8b0b222649796215613d84010902"
        },
        "date": 1790817871531,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 406.311609,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 58.920244,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1551.479167,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 75.079143,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "0975c842a0b5ea52fef6dd818adaea8be0f7c5c4",
          "message": "fix(ide-plugin): verifyPreview の golden との比較で、文字の縁の階調ほどの差は同じとみなす\n\nバイトで比べていたため、別の macOS（GitHub の runner）で描くと全 PNG が changed になった。\nCI で描いた 304 枚と golden の差は、最大 47 ピクセル・各チャンネル 9/255 で、どれも文字の縁の\n階調だけ。バイトが違うときは画像として読み、各チャンネルの差が 16/255 以内なら同じとみなす。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T10:33:22+09:00",
          "tree_id": "2b0e0bfed838e596c01d9af0b30054768fd5fa67",
          "url": "https://github.com/TBSten/katachi/commit/0975c842a0b5ea52fef6dd818adaea8be0f7c5c4"
        },
        "date": 1790819336093,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 413.540333,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 71.519193,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1595.48029,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 81.279113,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "c7ddaa40a06a32138e357f49279ce18ad96893b8",
          "message": "fix(ide-plugin): 確認用の出力先を手元のパスで決め打ちしていたテストを直し、integrationTest の補助ファイルを Role に入れる\n\nMultiTemplateRenderTest・MultiTemplateNewMenuTest・MultiTemplateInjectionTest は、PNG やダンプの\n既定の出力先（と、読むリポジトリ）を /Users/tbsten/dev/katachi/.local/... にしていたため、CI の\nrunner で FileNotFoundException になった。出力先は build/verify-multi-template、読むリポジトリは\nこのビルドの親にする。a98ea7d で足した integrationTest の RepositoryCopy.kt が、リポジトリ自身の\n定義で UnexpectedFile になっていたので、IdePluginTest の integrationTest を *Test 以外も含む形にする。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T10:50:59+09:00",
          "tree_id": "58db57e336b0854a40e9e92729eea3c95a9ab98b",
          "url": "https://github.com/TBSten/katachi/commit/c7ddaa40a06a32138e357f49279ce18ad96893b8"
        },
        "date": 1790820393445,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 261.01594,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 33.52246,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1026.442543,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 55.193169,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "0a32e004e0cf820cf8d530b93df43d95511e8bab",
          "message": "docs: ガイドと手順書の例から .module { } と modulePackage を外し、廃止予定の書き方を例に使わない方針を書く\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T18:01:23+09:00",
          "tree_id": "afc228aa47a4b83ce31f1201e0b3f09ba7ad2ce3",
          "url": "https://github.com/TBSten/katachi/commit/0a32e004e0cf820cf8d530b93df43d95511e8bab"
        },
        "date": 1790860807478,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 256.182808,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 52.28289,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 862.97977,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 59.00883,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "committer": {
            "email": "programmingcafeteria@gmail.com",
            "name": "tbsten",
            "username": "TBSten"
          },
          "distinct": true,
          "id": "398d5f35b1be8c390d61f358476597cfb2d39f84",
          "message": "ci: 実プロジェクトのベンチマークのぶれの幅を測る、手動起動のワークフローを足す\n\n同じコミットを、複数の runner で並行に、各 runner で JVM を立ち上げ直して数回測り、\nrunner 間と同じ runner の中のぶれ、続けて2回走らせたときの比の最悪値を要約に出す。\nbench-store の閾値（直前の1回と比べて 150%）を決める材料にする。gh-pages には書かず、\nコメントも付けない。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-01T22:26:35+09:00",
          "tree_id": "f36e1db23012814b6684bf3e506c6057e6b63370",
          "url": "https://github.com/TBSten/katachi/commit/398d5f35b1be8c390d61f358476597cfb2d39f84"
        },
        "date": 1790862159705,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 411.762374,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 69.555132,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1561.791575,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 81.555703,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      }
    ]
  }
}