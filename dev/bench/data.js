window.BENCHMARK_DATA = {
  "lastUpdate": 1793984646746,
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
          "id": "4b2841cc6df9593a180a19738f385b414adcbab5",
          "message": "docs(slides): 5分でわかる katachi のスライドに OGP・favicon・description・canonical を足す\n\nサイト本体と同じ og.png と favicon / アイコンを使い、twitter カード、en と ja の hreflang、\ntheme-color も出す。スライドの CSP の img-src に 'self' を足して favicon を読めるようにする。\n英語のタイトルの「(EN)」も外す。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-02T07:05:50+09:00",
          "tree_id": "657adc7128c4b061b35d22f3bf884aba5a9eb623",
          "url": "https://github.com/TBSten/katachi/commit/4b2841cc6df9593a180a19738f385b414adcbab5"
        },
        "date": 1790893825141,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.910427270994176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 49.51832831054878,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.972532202426606,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 53.45940822267206,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1330.9325299125887,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4178.876464384473,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12607.605870553376,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41145.873344355445,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1225.936791806013,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4127.978012709756,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12007.645338324448,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 40472.79954457143,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 42.880152191111115,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 123.03289498831698,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 336.9114062928572,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1817.18755455,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.7547310567215186,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.078630411875193,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 19.369339877316232,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 60.201412171568634,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.924861161655201,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14.569564550725781,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27.910654082516498,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 69.62140510747125,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 135.1489924,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 197.78144979999996,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 811.4621218,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1408.8438832,
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
          "id": "20828c85a900679ee16803317244f663450619f8",
          "message": "feat(install): 作業用ディレクトリの直下を HTML 2つと tmp/ だけにし、スクリプトの写しと cache/ を tmp/ の下へ移す\n\n直下には人が開く check-list.html と project-code-base-report.html、エージェントの作業場所の\ntmp/ だけを置く。katachi-install.sh の写しと cache/（lang・version・docs・maven-local・MANIFEST・\n取得したガイド）は tmp/ の下に置く。前の配置（直下に cache/ か katachi-install.sh がある）は、\ninit やサブコマンドを打ったときに tmp/ の下へ移し、MANIFEST の置き場所の列も書き換える。\n手順書（ja / en）、docs/AGENTS.md の表、prerelease の統合テストの手引きも合わせる。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-02T09:09:11+09:00",
          "tree_id": "53cc872e29006badbada1659ab9139308a31e75c",
          "url": "https://github.com/TBSten/katachi/commit/20828c85a900679ee16803317244f663450619f8"
        },
        "date": 1790948097137,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.295097822310067,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 43.671457462950976,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.592347188950339,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 49.81899267073171,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1364.2269333193349,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4385.248208289375,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12957.476005717841,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41605.9548559949,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1287.280435787135,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4329.127687678319,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12494.495349535551,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41723.91787814626,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 37.48548295204918,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 101.81483254962406,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 479.75918745999996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1850.6789061999996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.802685133809155,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.149004694089458,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 19.16386469538997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 59.80527457352942,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 10.280794933177978,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14.94651982566808,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27.72154853924267,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 69.4638889794253,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 152.8666394,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 221.79073079999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 846.2921162,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1535.9911637999999,
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
          "id": "2f760ae57215ae096a2503c3f7a1dd79a57120bb",
          "message": "docs: README.ja.md から IDE プラグインからの生成の案内を外す\n\n案内先のテンプレートのガイドに IDE プラグインの節が無いため（利用者の修正）。行の折り返しも整えた。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-11-01T13:57:15+09:00",
          "tree_id": "77aca0e56e1b761d35ef9210938743c05683b129",
          "url": "https://github.com/TBSten/katachi/commit/2f760ae57215ae096a2503c3f7a1dd79a57120bb"
        },
        "date": 1793510010982,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 3.6972300538236404,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 31.475472124197573,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 5.647631453863868,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 39.49155316230913,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 932.8317213649894,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2915.4841935125332,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 9112.980013370498,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 30775.26103595302,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 897.627934573405,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2926.573375828031,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 9094.097195810271,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 30485.973794983893,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 20.240189991331537,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 65.86446536849462,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 200.26210667272727,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 664.4507952916666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.013975284299568,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5.52355732611264,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 16.964603216829943,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 51.707408042328574,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 6.4665330497518125,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10.285366678988442,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 22.094701090774333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 59.14050997687038,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 104.751097,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 140.463943,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 561.5998248000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 956.7958928,
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
          "id": "d742b09b2f1d472ec763b5afd45f9153b77eb541",
          "message": "fix(intellij): テンプレートセット行のメンバの区切りを英語では \", \" にする\n\n区切りの「・」が直書きで、英語の IDE でもツールウィンドウの組の行に\n「Screen・ViewModel」と出ていた。区切りを KatachiStrings に移し、\n英語は \", \"、日本語は「・」にする。英語のプレビュー画像 14 枚を取り直した。\n\nprerelease の 10-4（文言の日英）を試しに回して見つかったもの。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-04T03:06:17+09:00",
          "tree_id": "67449bd950119e6d542e203083ae8aab572fe708",
          "url": "https://github.com/TBSten/katachi/commit/d742b09b2f1d472ec763b5afd45f9153b77eb541"
        },
        "date": 1793730934281,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4.827800454181113,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 40.464345732000005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.402928311980473,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 46.47031799883184,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1556.4582409912502,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4761.988398888847,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 14789.639559700701,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 49004.698489186994,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1405.8683472956745,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4704.447446110612,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 14598.106346091236,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 48336.01496445318,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 29.607697720149577,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 95.65247461861472,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 351.0591996666666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1405.7773375000002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 3.312211079324611,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 8.647760046366706,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 24.790640837141304,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 74.5136628968254,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 10.223370639997308,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 16.05478334352208,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 32.4452119259756,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 86.47678412735506,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 173.91213299999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 244.82270980000004,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 894.2212208000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1595.1859868,
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
          "id": "0733e7bbef3c68c03f77a84b8ed2485256fdae69",
          "message": "Merge branch 'claude/agitated-bell-6592c6' into landing/kdoc-and-deps-fixes\n\n依存更新で赤くなったテスト 4 件の追従（claude/agitated-bell-6592c6）を、\nKDoc の品質チェック（claude/kdoc-quality-check-5b6b64）の上に取り込む。\n\n衝突は katachi-konsist の internal/DeclarationText.kt と KonsistFacts.kt の 2 ファイル。\nどちらも追従側が「context(...) の前のアノテーションを読み直す回避処理」を消した範囲に、\nKDoc の整理が当たったもの。コードは追従側の版を取り、KDoc の整理だけをその上に\nかけ直した（コード行が追従側と一致することを、コメント行と空行を除いた diff で確認）。\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-04T09:41:39+09:00",
          "tree_id": "7329c6f312f036bb3dbf93a98c80555251503897",
          "url": "https://github.com/TBSten/katachi/commit/0733e7bbef3c68c03f77a84b8ed2485256fdae69"
        },
        "date": 1793809190363,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4.917741389134947,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 41.42528911853742,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.576608844855545,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 49.13384314390534,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1531.1490042107766,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5009.287622221278,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 15306.269621679845,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 50990.70325555817,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1448.8540845810896,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4935.588839964192,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 14687.379636655718,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 49857.81683286586,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 31.35637048023088,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 100.09721545523807,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 384.7361027666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1428.4924420499997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 3.3304452522844388,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 9.081177856278908,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25.463587243242856,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 79.88824382846154,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 10.55782217509144,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 16.59029235235595,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 33.31237429060109,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 89.41876430750987,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 171.6354506,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 246.794225,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 904.5740010000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1616.068809,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "376fa42201e665d9ab023f38c466569784a9c502",
          "message": "Merge pull request #1 from TBSten/claude/project-thread-rppq75\n\nfeat(diff): Git 差分から Role / Group の影響範囲レポートを出す katachiDiff (KATACHI-223)",
          "timestamp": "2026-11-06T01:52:45+09:00",
          "tree_id": "a950c4c5da90b3cb42e84f8a7fdb2e07ddc3cb40",
          "url": "https://github.com/TBSten/katachi/commit/376fa42201e665d9ab023f38c466569784a9c502"
        },
        "date": 1793898598783,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 2.409025669802685,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 22.50231032909348,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4.359032249649059,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 28.471774848984523,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1096.3523612028432,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3314.279698583975,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 11393.930811902439,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 36421.611836266224,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1051.3730044328502,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3389.957424678708,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 10622.66178644536,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 36173.90544273156,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 22.487371914578954,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 70.3780566030706,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 220.09594177111111,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 942.9345214833335,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.5828638265607387,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.242980261228345,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 20.22429558121622,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 62.47754317689394,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 7.362491737723363,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 11.84264762669179,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25.722469608629076,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 69.33132445176227,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 110.70807439999999,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 155.56604620000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 628.1864002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1106.4816906,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "c8c02ccd5485fcb0724adda825f6797fbafb572c",
          "message": "Merge pull request #2 from TBSten/claude/project-thread-48lio4\n\nfeat(cli): brew で配る katachi コマンドと :katachi-script モジュールを足す",
          "timestamp": "2026-11-06T01:54:24+09:00",
          "tree_id": "5567823e95e30e97b190e1867cc3c12ebc9e6a09",
          "url": "https://github.com/TBSten/katachi/commit/c8c02ccd5485fcb0724adda825f6797fbafb572c"
        },
        "date": 1793899579085,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 2.52200146083715,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 22.759116635942778,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4.933845987094886,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 28.09313062577603,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1229.7968394553077,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3904.3041060990918,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12725.381115888713,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41574.94102923722,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1173.659648530432,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3804.371093006698,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 12459.45522468163,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 41399.19588707642,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 23.013177434587252,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 77.2415828048433,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 221.32748653444446,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1160.3205366999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.7529218920645073,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.475818523335027,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 21.732396454328576,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 66.69421764655914,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 7.923183622993667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 12.673260899824701,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 26.846860148625886,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 73.93726550343915,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 123.527592,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 166.64485900000003,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 676.2440438000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1159.2835639999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "3645feb1ecf5eec967b2f45a81be37b94afbc3b3",
          "message": "Merge pull request #3 from TBSten/claude/project-thread-9q4lmc\n\ndocs: v0.3 のドキュメントを写しにして凍結し、現行の版を v0.4 にする",
          "timestamp": "2026-11-06T02:16:12+09:00",
          "tree_id": "1088e518e17bb2654068ac26bdda297e12fa9410",
          "url": "https://github.com/TBSten/katachi/commit/3645feb1ecf5eec967b2f45a81be37b94afbc3b3"
        },
        "date": 1793900929092,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4.949259001009314,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 41.30470362291666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 8.015809102477231,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 47.660441664515304,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1578.7332560644509,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5090.939219540974,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 15538.944713347842,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 50075.83840753439,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1483.1327737812744,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4901.374780920148,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 14900.62173787693,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 49013.31194813407,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 29.77904737073593,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 98.8620249654762,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 392.12993625666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1396.65618545,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 3.4204710672641374,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 8.76775779627779,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 24.73450080451657,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 75.72670942037037,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 10.878997455018583,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 16.4357836046048,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 33.71551068106084,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 87.55602500869564,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 181.97296999999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 251.45564919999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 916.0691236,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1604.4454696,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8e6e8b927878291a2ade460fd4d80087f7bc1020",
          "message": "Merge pull request #4 from TBSten/claude/project-thread-1ufbp6\n\ndocs: 公開サイトの root を最新リリースにし、main の書きかけは /dev/（v0.4-dev）に出す（KATACHI-149）",
          "timestamp": "2026-11-06T02:57:17+09:00",
          "tree_id": "921c9f5d82a897efbf162b15e5fe218fe81f4a66",
          "url": "https://github.com/TBSten/katachi/commit/8e6e8b927878291a2ade460fd4d80087f7bc1020"
        },
        "date": 1793902359367,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 3.0074426882463063,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 28.031464271627065,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 5.978904405332954,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 40.59964753697959,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1473.6685390972295,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4641.326520574995,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 14986.46037656203,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 48921.38499043744,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1375.2599202517827,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4544.005858294738,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 14764.456056627629,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 49187.59038826946,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 26.100046220779223,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 87.19716945108698,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 251.23422611249998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1256.1406475,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 3.2756119352449367,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 9.15179261564441,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 26.121625117642445,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 80.48958122476924,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.515868968893596,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 15.254771176255264,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 32.73708460140138,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 88.95574922173914,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 156.80575900000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 210.3601556,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 809.6308194000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1382.7727416000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "33dc778be1043d3ca5e1b59a060f03866c6d52be",
          "message": "Merge pull request #5 from TBSten/claude/project-thread-xczyg5\n\nperf(dsl): Glob.compile をパターン文字列で memo 化し、Nearby の候補を最初の違反まで遅延する（KATACHI-236, KATACHI-203, KATACHI-209）",
          "timestamp": "2026-11-06T03:12:24+09:00",
          "tree_id": "7f0b3d705769bf0976ff7c973b7ebb4a8ed431de",
          "url": "https://github.com/TBSten/katachi/commit/33dc778be1043d3ca5e1b59a060f03866c6d52be"
        },
        "date": 1793903305315,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4.187196736519243,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 35.77980676253134,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 5.396355519348738,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 42.49721850517583,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 715.4709183317982,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2330.4409983775017,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6803.061645677243,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 22555.408887849313,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 652.8790129593725,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2186.91003371657,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6616.367420982849,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 22389.585606467586,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 24.752432533205152,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 76.9282625519943,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 234.84529063333338,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1051.40806155,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1.8102469220721287,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4.144959065874376,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 11.035422468651547,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 34.42560435968444,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 7.711409434637676,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10.822800550261235,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 19.479909914609927,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 45.35874076553559,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 110.39363699999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 166.6267016,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 666.5046737999999,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1192.611082,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8df5ac18f8bd1b357b9054929de7a40d1a3871d9",
          "message": "Merge pull request #7 from TBSten/claude/project-thread-v12slw\n\ndocs: first-architecture の失敗時の出力例を手順 3 の定義にそろえる (KATACHI-193)",
          "timestamp": "2026-11-06T03:31:07+09:00",
          "tree_id": "c07f36437a5c64a8fbbb5ff610a3ecb796b01aa4",
          "url": "https://github.com/TBSten/katachi/commit/8df5ac18f8bd1b357b9054929de7a40d1a3871d9"
        },
        "date": 1793904838798,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 3.7664209167452327,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 33.1214867049964,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4.887131530132864,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 39.138575171651176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 566.5914116321992,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1793.7107936838424,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 5566.089871488233,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 18520.65464910418,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 540.5759643452686,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1708.42342412251,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 5266.66312018713,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 17830.55157030046,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 15.589951987930922,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 58.930055356424745,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 180.96802116287878,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 636.8677670499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1.3060133667449472,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3.1236524105208003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 8.205195784753304,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 26.98158454828252,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5.3664567159346195,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.977357841869993,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 14.25717142669186,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 36.434550437065006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 90.96284560000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 134.228312,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 525.8225632,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 951.2630860000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "85f86fe4a308941598e46897a80cc939962b536a",
          "message": "Merge pull request #11 from TBSten/claude/project-thread-xdku8z\n\nci: pull request ごとに katachiDiff の Markdown レポートをコメントする (KATACHI-242, KATACHI-223)",
          "timestamp": "2026-11-06T18:14:09+09:00",
          "tree_id": "b3b7d745dc6b24a18f23471ea48ea840439f38fd",
          "url": "https://github.com/TBSten/katachi/commit/85f86fe4a308941598e46897a80cc939962b536a"
        },
        "date": 1793957463860,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4.216390499968797,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 35.87666733439907,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 5.188526746621604,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 41.7546358262635,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 714.0403867106853,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2283.3936437016537,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6733.91157488695,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 22577.79528296476,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 651.5423514640372,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2160.2046885128243,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6690.184179306821,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 22618.674723531098,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 23.472776911915183,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 76.72416968034187,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 233.78956893333333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1049.2418712499998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1.7582761226368895,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 4.087799217901013,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 10.694607990818634,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 33.676106311694916,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 7.331632807590975,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 9.715073229271749,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17.762960300476603,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 42.23242102571917,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 103.0565568,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 161.2193578,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 641.1368512,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1142.0164453999998,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "a3c24ae1f4da23a2f2b58aa9243271ce53577838",
          "message": "Merge pull request #12 from TBSten/claude/project-thread-tlijka\n\nchore(ide-plugin): buildPlugin に -Pkatachi.timestampVersion で日付入りの版を付ける (KATACHI-246, KATACHI-220)",
          "timestamp": "2026-11-06T19:01:33+09:00",
          "tree_id": "2d0be72df1c5146bd317e3ad3ead99c987213b49",
          "url": "https://github.com/TBSten/katachi/commit/a3c24ae1f4da23a2f2b58aa9243271ce53577838"
        },
        "date": 1793960214847,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 2.9500487672777838,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 27.250744848248548,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4.713205000144805,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 36.90678755626389,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 907.2812816600517,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2925.311079338674,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 9003.929018836325,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 29770.93235702371,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 831.5246403602205,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2780.939436212376,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 8706.958960933925,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 29168.161869715015,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 26.598467036561402,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 93.14563043636363,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 272.12869118750007,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1329.9352692,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.323502858917236,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5.546783173467068,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 14.947315594647876,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 44.59235901666666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.886095972937824,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14.146769065416265,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 24.38044457990009,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 55.460853098273276,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 139.60648780000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 200.6529928,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 805.141891,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1451.3424698,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "093a66a2dc6b47c86ead27bfc7ea7da105826cc6",
          "message": "Merge pull request #10 from TBSten/claude/project-thread-uqnr00\n\nfeat(ide-plugin): KATACHI-220 JetBrains MCP Server に katachi の tool 7 本を足す（KATACHI-230 / 237 / 239）",
          "timestamp": "2026-11-06T19:54:00+09:00",
          "tree_id": "181d890e6fe73ad79c295cc0470e7d63a5bc21e2",
          "url": "https://github.com/TBSten/katachi/commit/093a66a2dc6b47c86ead27bfc7ea7da105826cc6"
        },
        "date": 1793963380605,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 5.435621996896441,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 46.26838114904863,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 7.789660665972261,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 54.33643722904189,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 939.7776787766886,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2987.2246197917675,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 8757.057151003448,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 29975.169125401902,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 843.5730657095206,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2806.983330598276,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 8666.968284972032,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 28995.68498751659,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 29.156386602663495,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 102.61767224090227,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 318.4985642428572,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1369.74771215,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.285019675386003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5.448133624675582,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 15.037448360790993,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 44.80347061807861,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 10.489471848606417,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 13.429547046277122,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25.70163446347226,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 57.56865675523809,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 144.654541,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 217.456522,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 882.8664176,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1522.645535,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "55ec84d62602a114aefa3716e1430412f9189fae",
          "message": "Merge pull request #15 from TBSten/claude/project-thread-ij2ox8\n\ntest(diff): KATACHI-250 パス省略表示の回帰テストを追加",
          "timestamp": "2026-11-06T21:03:16+09:00",
          "tree_id": "2bfefe823d9f2a389d49031cedc6a0440ff2c9af",
          "url": "https://github.com/TBSten/katachi/commit/55ec84d62602a114aefa3716e1430412f9189fae"
        },
        "date": 1793967519334,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4.921051152128522,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 40.87182891404082,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 6.239589003788206,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 44.588957915101005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 966.0059929436941,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2992.103781555452,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 8743.90234844072,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 28902.45788137042,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 860.854389810827,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2860.4707477382735,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 8517.241902849122,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 28882.866743481765,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 30.269241107134462,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 92.95703956818183,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 301.9117905714286,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1262.322389,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2.276592318667581,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5.589233644738874,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13.789093259898674,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 43.06189767888914,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.554892322363607,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 12.850800770532306,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 22.77421030172288,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 52.91792642165992,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 150.9215666,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 227.6708424,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 864.9142718,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1591.5640404,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6b47fe287619f49ceff5ad9b089fc27aaea7cbc3",
          "message": "Merge pull request #17 from TBSten/claude/project-thread-zzokoe\n\nfix: KATACHI-251 katachiDiff の Heat 列を Importance にして GitHub の表崩れを防ぐ（KATACHI-223 / KATACHI-242 の続き）",
          "timestamp": "2026-11-06T21:05:29+09:00",
          "tree_id": "b44b788bfd9f58bf5de28a0abc4dfe06e27f163c",
          "url": "https://github.com/TBSten/katachi/commit/6b47fe287619f49ceff5ad9b089fc27aaea7cbc3"
        },
        "date": 1793968816998,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 3.819410734644309,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 31.791328074612732,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4.548217901698908,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 32.587415608565934,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 564.4883387135642,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1792.6107055594207,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 5287.064479110524,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 17686.415844578154,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 517.8991401908169,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1703.898953792656,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 5124.872835740408,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 17235.899157725584,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 14.994594039197926,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 58.71541414193108,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 179.6409267378788,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 622.6460798250001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1.3228339299539258,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3.133675415235383,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 8.272094417493687,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 27.601550806858928,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5.15293831189677,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.21998816669922,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13.630674985356254,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 35.363461804264375,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 96.066166,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 139.7794972,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 547.9538816,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 962.4973479999999,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "af79f115d8e6f8b9047788d431ed0a29353784ba",
          "message": "Merge pull request #16 from TBSten/claude/project-thread-cox6eq\n\ntest(ide-plugin): AutoReloadTest の実時間の余裕を 50 ms から 900 ms に広げ、時刻で検証する (KATACHI-247, KATACHI-245)",
          "timestamp": "2026-11-06T21:44:38+09:00",
          "tree_id": "706db18124f25a3840c9eecbf2d85f3e13c0baa2",
          "url": "https://github.com/TBSten/katachi/commit/af79f115d8e6f8b9047788d431ed0a29353784ba"
        },
        "date": 1793969968137,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 2.447326972783788,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 21.878368976445323,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3.533454722351988,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 28.34187081407332,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 661.4424003292104,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2082.9052860824186,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6331.99869483355,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21124.224447153054,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 630.2693937452534,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1961.1084271731575,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6146.0968588446685,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 20852.758964505345,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 22.425995003711073,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 68.32717226689655,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 203.93712147999997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 740.8127785,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1.5711850135949117,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3.8329334902571945,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 10.061873865977287,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 30.982754716455116,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 6.311188288426263,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 8.524545262362457,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 15.666479181891162,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 38.266256012491205,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 97.2725658,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 135.06749480000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 582.3435311999999,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 997.1457492,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6a9c3c66200ea08863a99b0c70cb98b1a97510d2",
          "message": "Merge pull request #14 from TBSten/claude/project-thread-lzuued\n\nfeat: KATACHI-234 .module { } 系の API を削除する（KATACHI-78 の削除、KATACHI-233 の置き換え）",
          "timestamp": "2026-11-06T22:02:03+09:00",
          "tree_id": "7d914b0a21a1f7027e26e6c916b76a54c19656b1",
          "url": "https://github.com/TBSten/katachi/commit/6a9c3c66200ea08863a99b0c70cb98b1a97510d2"
        },
        "date": 1793971218972,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4.861588492210341,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 40.78006658840816,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 5.662000700834123,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 43.03906094764377,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 169.6891002720128,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 376.9126812365356,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 288.9650066974915,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 487.34426200408933,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 82.76408630769137,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 254.81904383990795,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 81.8373107429517,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 257.94478004279927,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5.626983310772159,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 13.052601025953923,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 5.508100293915556,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 14.607525052016769,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1.2677618839251281,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2.119557550637911,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 2.187136873506355,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3.238509809062633,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 9.735942411086384,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 12.978925625592325,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 10.818333306910954,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 16.775451081916835,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 107.29753000000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 191.1103504,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 794.5246010000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1507.9674728,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "9062763e79a29a9bb7c773405badf8b71ae0f7bb",
          "message": "Merge pull request #21 from TBSten/claude/project-thread-fsvko3\n\nrefactor: KATACHI-76 モジュール capture と modulePlacements の残りを削除する（KATACHI-234 の続き）",
          "timestamp": "2026-11-06T23:42:23+09:00",
          "tree_id": "08de89fd81c05ea953d0c314ba350c08c1d44953",
          "url": "https://github.com/TBSten/katachi/commit/9062763e79a29a9bb7c773405badf8b71ae0f7bb"
        },
        "date": 1793977058582,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 2.5819721110274534,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 23.02714107460469,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3.374628856110136,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 27.344686551087086,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 118.62541023836795,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 281.29999008258153,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 212.59552549762466,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 368.93631064314803,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 66.69718784803699,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 207.97430047585286,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 67.43744836687867,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 210.34676178824967,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4.476678403042606,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10.54868710699861,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 4.532683430111707,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 10.74223725994375,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 0.9239937029337503,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1.6749265656485561,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1.7119945836657713,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 2.4846769400266537,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 7.623329717234364,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10.794372142318617,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 8.72580987252024,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 13.043813892151496,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 83.36968099999999,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 132.8517292,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 633.1359776,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1143.651512,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "ea5a472895ce8a47ee2e17b885e6dc1d3f1f0e59",
          "message": "Merge pull request #19 from TBSten/claude/project-thread-i9r7d2\n\nKATACHI-228 / 229: katachi CLI の全サブコマンドを Kotlin に移し、sh を同梱しない（親 KATACHI-226）",
          "timestamp": "2026-11-06T23:50:19+09:00",
          "tree_id": "bec42e7deec03b0587ab6919f44e286ade44b8a9",
          "url": "https://github.com/TBSten/katachi/commit/ea5a472895ce8a47ee2e17b885e6dc1d3f1f0e59"
        },
        "date": 1793978352459,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 3.653454538321533,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 29.97573297075104,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3.940326285308154,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 29.344669347607088,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 91.49039346646596,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 210.85194244714094,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 154.55449119918728,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 281.6958805779493,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 49.20009637078547,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 152.17924226795836,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 50.21478861807397,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 155.45356902990363,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 3.0233675384868315,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 6.983125832175462,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 3.0237630390672248,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 7.5840999899585455,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 0.6802003948160655,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1.1217833327021693,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1.1111930446955038,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1.7545579317607367,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5.520397000082292,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.756505373967597,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6.366086591543942,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 10.20324178735932,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 75.4480674,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 119.0645464,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 513.9181845999999,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 901.8455985999999,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "18413b23d66f85999eee02ef7d62f58c7577c2e4",
          "message": "Merge pull request #23 from TBSten/claude/project-thread-7109ud\n\ndocs(katachi-script): InProcess の KDoc から sh 委譲の一文を消す (KATACHI-256, KATACHI-228)",
          "timestamp": "2026-11-07T00:23:01+09:00",
          "tree_id": "0454bfd73137603a77d2970b8e3835ed63949d78",
          "url": "https://github.com/TBSten/katachi/commit/18413b23d66f85999eee02ef7d62f58c7577c2e4"
        },
        "date": 1793979527431,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4.226325430795132,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 36.54725317782468,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4.999468429915013,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 42.24954637857559,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 122.85484872937641,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 284.9704437616705,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 222.68353938336594,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 386.97402471849483,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 64.08207764270145,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 200.35238230222438,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 64.85906189736285,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 202.19579048199367,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4.501256189807342,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 11.12080299390809,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 4.5563294912693175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 11.003571076689903,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 0.9550123538970718,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1.6671072350976939,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1.6373966786458545,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 2.4655082451017116,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 8.336028011717813,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 11.218920992820122,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 9.250293658620198,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 14.873664397002438,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 84.7592418,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 146.03292240000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 653.2093052,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1178.8358838,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6d374367135bafe8e6605764dee5a63af7c906ee",
          "message": "Merge pull request #24 from TBSten/claude/KATACHI-263-diff-fixed-importance-scale\n\nfeat(diff): Importance を PR 内の最大値ではなく固定の基準値で測る (KATACHI-263, KATACHI-261)",
          "timestamp": "2026-11-07T00:58:26+09:00",
          "tree_id": "0b459b270e83a7f73a6d54e34c9493fa03106681",
          "url": "https://github.com/TBSten/katachi/commit/6d374367135bafe8e6605764dee5a63af7c906ee"
        },
        "date": 1793981606772,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 3.806798270819351,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 33.08878183716737,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4.399697861419015,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 40.075514448233854,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 93.49331231227133,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 210.59901648571417,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 156.6044190261755,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 284.6601725018432,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 50.3894381242976,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 159.45299300592382,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 51.52842394514283,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 162.68548510360725,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 3.071038465510413,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.855714757755936,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 3.3484466314374783,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 7.661597264103079,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 0.7051855419722084,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1.1570498140845291,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1.1492356195521363,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 1.773310303705441,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5.5940800403786906,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7.722906747539204,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6.252729961088109,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 10.831933382346167,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 71.1674826,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 126.15284580000002,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 557.6889060000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 973.6173408000001,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "b64e1a4d720b003089be8c1f4588f8e0e57f2a15",
          "message": "Merge pull request #25 from TBSten/claude/KATACHI-101-script-katachi-kts\n\nfeat(cli, ide): *.katachi.kts で katachi のコマンドを前置きなしで書いて実行する（IDE の補完は CLI から定義をもらう）(KATACHI-101, KATACHI-100, KATACHI-256, KATACHI-151)",
          "timestamp": "2026-11-07T01:48:07+09:00",
          "tree_id": "66fa815d94a476d8dc01ce654fd4e20cfcbf7e9c",
          "url": "https://github.com/TBSten/katachi/commit/b64e1a4d720b003089be8c1f4588f8e0e57f2a15"
        },
        "date": 1793984642619,
        "tool": "jmh",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 2.623792647119056,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 24.080496253143025,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3.4847963494696303,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 27.504315397219138,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123.54601270220034,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 282.40713830339985,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 217.96953787479202,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 385.8387171820971,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 69.05722261089937,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 212.11680149676036,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 68.75722840031031,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 212.57933793048974,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4.521703842021258,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 11.035426151180946,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 4.694342797934905,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 11.115465438734397,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1.0261775937998436,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 1.6680669333542706,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1.7583703178423442,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 2.5753409604227353,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 8.009217254462346,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10.659087551544284,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 8.91116871288369,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 13.466177071729438,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 85.744364,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 135.410913,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 652.1067006,
            "unit": "ms/op",
            "extra": "iterations: 1\nforks: 5\nthreads: 1"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 1147.1907078,
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
          "id": "4b2841cc6df9593a180a19738f385b414adcbab5",
          "message": "docs(slides): 5分でわかる katachi のスライドに OGP・favicon・description・canonical を足す\n\nサイト本体と同じ og.png と favicon / アイコンを使い、twitter カード、en と ja の hreflang、\ntheme-color も出す。スライドの CSP の img-src に 'self' を足して favicon を読めるようにする。\n英語のタイトルの「(EN)」も外す。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-02T07:05:50+09:00",
          "tree_id": "657adc7128c4b061b35d22f3bf884aba5a9eb623",
          "url": "https://github.com/TBSten/katachi/commit/4b2841cc6df9593a180a19738f385b414adcbab5"
        },
        "date": 1790893827000,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4947330.430251916,
            "range": "± 436",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52132159.630243905,
            "range": "± 10225",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4522942.917893747,
            "range": "± 11621",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 20132532.84642375,
            "range": "± 96494",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1847122.058743399,
            "range": "± 2964",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5860714.296473381,
            "range": "± 24310",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17420577.449546017,
            "range": "± 4378",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56900703.20440817,
            "range": "± 258511",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1723849.9447234732,
            "range": "± 7241",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5743964.908730605,
            "range": "± 81892",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 16971232.231364165,
            "range": "± 178407",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56332451.16995919,
            "range": "± 538471",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656845.96444447,
            "range": "± 11",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381280.86437905,
            "range": "± 33",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751136.857143,
            "range": "± 112",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987748,
            "range": "± 36",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4158297.6669482416,
            "range": "± 1443",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10140578.353650216,
            "range": "± 1365",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27133335.317086767,
            "range": "± 33686",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 79780129.97176471,
            "range": "± 2889",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16152383.370607495,
            "range": "± 3156",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 21643757.56085228,
            "range": "± 172458",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 39564257.051363155,
            "range": "± 874",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 93595545.31954023,
            "range": "± 10713",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17558104,
            "range": "± 37404",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31840084.8,
            "range": "± 144881",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69728316.8,
            "range": "± 50989",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174031617.6,
            "range": "± 173982",
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
          "id": "20828c85a900679ee16803317244f663450619f8",
          "message": "feat(install): 作業用ディレクトリの直下を HTML 2つと tmp/ だけにし、スクリプトの写しと cache/ を tmp/ の下へ移す\n\n直下には人が開く check-list.html と project-code-base-report.html、エージェントの作業場所の\ntmp/ だけを置く。katachi-install.sh の写しと cache/（lang・version・docs・maven-local・MANIFEST・\n取得したガイド）は tmp/ の下に置く。前の配置（直下に cache/ か katachi-install.sh がある）は、\ninit やサブコマンドを打ったときに tmp/ の下へ移し、MANIFEST の置き場所の列も書き換える。\n手順書（ja / en）、docs/AGENTS.md の表、prerelease の統合テストの手引きも合わせる。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-02T09:09:11+09:00",
          "tree_id": "53cc872e29006badbada1659ab9139308a31e75c",
          "url": "https://github.com/TBSten/katachi/commit/20828c85a900679ee16803317244f663450619f8"
        },
        "date": 1790948099628,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4945872.596839865,
            "range": "± 385",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52152399.27215542,
            "range": "± 37136",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 4536416.408819199,
            "range": "± 10198",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 20102442.427317075,
            "range": "± 272849",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1838160.9012683071,
            "range": "± 15",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5908469.089444093,
            "range": "± 100758",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17417875.86096675,
            "range": "± 68",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56879181.97414966,
            "range": "± 840218",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1716654.6597042468,
            "range": "± 4226",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5809108.073846438,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 16920673.199360862,
            "range": "± 259206",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 56380011.78061225,
            "range": "± 1890",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656830.8308743,
            "range": "± 21",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381226.01704264,
            "range": "± 24",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751400.3200002,
            "range": "± 10",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987746.4,
            "range": "± 29",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 4159126.722914408,
            "range": "± 558",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 10045499.351273606,
            "range": "± 13243",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 27044404.12765222,
            "range": "± 1952",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 80072768.00000001,
            "range": "± 3112",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16159822.062987695,
            "range": "± 20816",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 21617304.82285788,
            "range": "± 122373",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 39556694.92232506,
            "range": "± 3463",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 93595433.2183908,
            "range": "± 10774",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17554902.4,
            "range": "± 53017",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31838766.4,
            "range": "± 146225",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69728056,
            "range": "± 39475",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174004028.8,
            "range": "± 478706",
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
          "id": "2f760ae57215ae096a2503c3f7a1dd79a57120bb",
          "message": "docs: README.ja.md から IDE プラグインからの生成の案内を外す\n\n案内先のテンプレートのガイドに IDE プラグインの節が無いため（利用者の修正）。行の折り返しも整えた。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-11-01T13:57:15+09:00",
          "tree_id": "77aca0e56e1b761d35ef9210938743c05683b129",
          "url": "https://github.com/TBSten/katachi/commit/2f760ae57215ae096a2503c3f7a1dd79a57120bb"
        },
        "date": 1793510012948,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4946551.812512423,
            "range": "± 1181",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52130999.731092796,
            "range": "± 45279",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 6046887.569758461,
            "range": "± 3993",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 21667144.404577684,
            "range": "± 21525",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2333651.1231084797,
            "range": "± 33221",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7406323.815623289,
            "range": "± 181761",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 22141740.96834715,
            "range": "± 531062",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 75219621.82841605,
            "range": "± 4936623",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2235046.00777962,
            "range": "± 2059",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7360908.326324235,
            "range": "± 10537",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 22373031.339912012,
            "range": "± 1540110",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 75742062.30610582,
            "range": "± 39123",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656779.96732017,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381121.02795696,
            "range": "± 3",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750779.490909,
            "range": "± 42",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777986495.6666665,
            "range": "± 402",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5691673.32041601,
            "range": "± 3642",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14849529.868371706,
            "range": "± 210427",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 41743402.31047199,
            "range": "± 31332",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 125279285.95995307,
            "range": "± 2692138",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17681650.542821623,
            "range": "± 7531",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 26640758.27072554,
            "range": "± 51559",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 53644603.75082645,
            "range": "± 844768",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 137162857.00818947,
            "range": "± 39162",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 19726731.2,
            "range": "± 63502",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 34107740.8,
            "range": "± 136801",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 71838265.6,
            "range": "± 216897",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 176232172.8,
            "range": "± 177863",
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
          "id": "d742b09b2f1d472ec763b5afd45f9153b77eb541",
          "message": "fix(intellij): テンプレートセット行のメンバの区切りを英語では \", \" にする\n\n区切りの「・」が直書きで、英語の IDE でもツールウィンドウの組の行に\n「Screen・ViewModel」と出ていた。区切りを KatachiStrings に移し、\n英語は \", \"、日本語は「・」にする。英語のプレビュー画像 14 枚を取り直した。\n\nprerelease の 10-4（文言の日英）を試しに回して見つかったもの。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-04T03:06:17+09:00",
          "tree_id": "67449bd950119e6d542e203083ae8aab572fe708",
          "url": "https://github.com/TBSten/katachi/commit/d742b09b2f1d472ec763b5afd45f9153b77eb541"
        },
        "date": 1793730979930,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4957929.352542705,
            "range": "± 3443",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52260780.015999995,
            "range": "± 35065",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 6089693.103091545,
            "range": "± 8997",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 21913189.293801807,
            "range": "± 20726",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2396827.5388177764,
            "range": "± 129691",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7409213.065492426,
            "range": "± 179286",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 22715210.066413343,
            "range": "± 405046",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 74362071.37268293,
            "range": "± 102625",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2198765.5817983127,
            "range": "± 8789",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7369728.4368677195,
            "range": "± 3510",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 22928437.17040568,
            "range": "± 632752",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 72790431.41420199,
            "range": "± 1804161",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656807.18581195,
            "range": "± 7",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381206.5489178,
            "range": "± 11",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751208.2666667,
            "range": "± 5",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987748.4,
            "range": "± 37",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5681264.447057741,
            "range": "± 13757",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14797093.47048386,
            "range": "± 295566",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 41762888.638376996,
            "range": "± 2305",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 125627335.12486775,
            "range": "± 2132079",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17653522.467790533,
            "range": "± 21206",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 26113633.04945893,
            "range": "± 209778",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 53488101.26727272,
            "range": "± 848313",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 138974582.98840576,
            "range": "± 2414680",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 19758473.6,
            "range": "± 196726",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 34053291.2,
            "range": "± 419550",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 73561523.2,
            "range": "± 89295",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174703921.6,
            "range": "± 79565",
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
          "id": "0733e7bbef3c68c03f77a84b8ed2485256fdae69",
          "message": "Merge branch 'claude/agitated-bell-6592c6' into landing/kdoc-and-deps-fixes\n\n依存更新で赤くなったテスト 4 件の追従（claude/agitated-bell-6592c6）を、\nKDoc の品質チェック（claude/kdoc-quality-check-5b6b64）の上に取り込む。\n\n衝突は katachi-konsist の internal/DeclarationText.kt と KonsistFacts.kt の 2 ファイル。\nどちらも追従側が「context(...) の前のアノテーションを読み直す回避処理」を消した範囲に、\nKDoc の整理が当たったもの。コードは追従側の版を取り、KDoc の整理だけをその上に\nかけ直した（コード行が追従側と一致することを、コメント行と空行を除いた diff で確認）。\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-04T09:41:39+09:00",
          "tree_id": "7329c6f312f036bb3dbf93a98c80555251503897",
          "url": "https://github.com/TBSten/katachi/commit/0733e7bbef3c68c03f77a84b8ed2485256fdae69"
        },
        "date": 1793809192766,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4958639.6672421815,
            "range": "± 2316",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52280569.17619048,
            "range": "± 25415",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 6088089.402695883,
            "range": "± 27348",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 21701326.01944251,
            "range": "± 132893",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2319565.1505563166,
            "range": "± 1189",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7598774.90980122,
            "range": "± 115617",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 23470407.69626295,
            "range": "± 325275",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 76372617.575197,
            "range": "± 3306212",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2169266.211086205,
            "range": "± 53125",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7563153.604552338,
            "range": "± 312806",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 21761862.64417601,
            "range": "± 519552",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 75936180.78731707,
            "range": "± 3133345",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656812.35151514,
            "range": "± 3",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381219.1047618,
            "range": "± 12",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751209.3333335,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987751.2,
            "range": "± 25",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5622425.381136381,
            "range": "± 73659",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14696557.927271873,
            "range": "± 1749",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 41764407.27892892,
            "range": "± 361",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 126905747.44738463,
            "range": "± 103685",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17675987.146480184,
            "range": "± 15157",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 26340468.71684923,
            "range": "± 506577",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 53496112.3547541,
            "range": "± 837853",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 137588202.9786561,
            "range": "± 1290236",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 19769812.8,
            "range": "± 210423",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 34124832,
            "range": "± 131804",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 73613585.6,
            "range": "± 143585",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174666675.2,
            "range": "± 683882",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "376fa42201e665d9ab023f38c466569784a9c502",
          "message": "Merge pull request #1 from TBSten/claude/project-thread-rppq75\n\nfeat(diff): Git 差分から Role / Group の影響範囲レポートを出す katachiDiff (KATACHI-223)",
          "timestamp": "2026-11-06T01:52:45+09:00",
          "tree_id": "a950c4c5da90b3cb42e84f8a7fdb2e07ddc3cb40",
          "url": "https://github.com/TBSten/katachi/commit/376fa42201e665d9ab023f38c466569784a9c502"
        },
        "date": 1793898601440,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4959278.116589582,
            "range": "± 1166",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52265945.84521862,
            "range": "± 24480",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 6054825.516798081,
            "range": "± 29545",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 21845629.72217145,
            "range": "± 28499",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2354674.9764188607,
            "range": "± 2690",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7400878.283442326,
            "range": "± 187993",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 23674365.744133882,
            "range": "± 312",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 75284718.06051949,
            "range": "± 1497223",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2199756.5681909025,
            "range": "± 54442",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7363098.325202664,
            "range": "± 14024",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 22054394.816783488,
            "range": "± 924523",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 74932700.93700416,
            "range": "± 4872309",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656786.6924104,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381132.9300493,
            "range": "± 8",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750831.8666668,
            "range": "± 48",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987148.8,
            "range": "± 790",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5682352.546949977,
            "range": "± 19421",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14981725.625115976,
            "range": "± 800",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 41764083.85720949,
            "range": "± 1109",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 126964408.94015153,
            "range": "± 2305",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17621392.66777347,
            "range": "± 67949",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 26409909.18619632,
            "range": "± 29139",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 54117050.04002011,
            "range": "± 92653",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 138593256.25991628,
            "range": "± 3099889",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 19762395.2,
            "range": "± 120589",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 34059012.8,
            "range": "± 379873",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 73576347.2,
            "range": "± 92777",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174753124.8,
            "range": "± 156577",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "c8c02ccd5485fcb0724adda825f6797fbafb572c",
          "message": "Merge pull request #2 from TBSten/claude/project-thread-48lio4\n\nfeat(cli): brew で配る katachi コマンドと :katachi-script モジュールを足す",
          "timestamp": "2026-11-06T01:54:24+09:00",
          "tree_id": "5567823e95e30e97b190e1867cc3c12ebc9e6a09",
          "url": "https://github.com/TBSten/katachi/commit/c8c02ccd5485fcb0724adda825f6797fbafb572c"
        },
        "date": 1793899581909,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4959275.523843529,
            "range": "± 1138",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52257570.90983019,
            "range": "± 11581",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 6047862.757117381,
            "range": "± 1685",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 21656565.783307545,
            "range": "± 260953",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2355642.5915266275,
            "range": "± 2079",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7522592.470049131,
            "range": "± 4545",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 23655131.14800315,
            "range": "± 30801",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 76394376.23124911,
            "range": "± 3068389",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2235049.7273331573,
            "range": "± 4469",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7367181.378280379,
            "range": "± 513",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 23313324.5179905,
            "range": "± 19183",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 76107880.22590823,
            "range": "± 3203869",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656788.2722048,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381153.1396011,
            "range": "± 7",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750845.1111112,
            "range": "± 53",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987751.2,
            "range": "± 25",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5692718.033820792,
            "range": "± 3253",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14789180.475888249,
            "range": "± 307191",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 41764293.45109353,
            "range": "± 472",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 126964821.75311828,
            "range": "± 1142",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17658790.441605665,
            "range": "± 26081",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 26387704.405794494,
            "range": "± 429293",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 52862292.088682786,
            "range": "± 171389",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 137145624.66455027,
            "range": "± 792254",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 19763420.8,
            "range": "± 119312",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 34091875.2,
            "range": "± 138516",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 73576076.8,
            "range": "± 97981",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174766452.8,
            "range": "± 127783",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "3645feb1ecf5eec967b2f45a81be37b94afbc3b3",
          "message": "Merge pull request #3 from TBSten/claude/project-thread-9q4lmc\n\ndocs: v0.3 のドキュメントを写しにして凍結し、現行の版を v0.4 にする",
          "timestamp": "2026-11-06T02:16:12+09:00",
          "tree_id": "1088e518e17bb2654068ac26bdda297e12fa9410",
          "url": "https://github.com/TBSten/katachi/commit/3645feb1ecf5eec967b2f45a81be37b94afbc3b3"
        },
        "date": 1793900933579,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4959356.6014731005,
            "range": "± 1210",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52258762.767687075,
            "range": "± 13420",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 6064731.608618234,
            "range": "± 14016",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 21882265.262577314,
            "range": "± 21142",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2392992.3562606196,
            "range": "± 121206",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7525144.125272433,
            "range": "± 511",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 23055679.458856832,
            "range": "± 986089",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 73360681.91961226,
            "range": "± 1775356",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2245218.2188007613,
            "range": "± 180389",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7324517.025859575,
            "range": "± 286930",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 22028100.81555183,
            "range": "± 943836",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 74098466.07176296,
            "range": "± 2315137",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656807.72294374,
            "range": "± 4",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381218.77523816,
            "range": "± 11",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751307.3866668,
            "range": "± 156",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987751.6,
            "range": "± 37",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5635389.686027844,
            "range": "± 94895",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14829458.01531593,
            "range": "± 175482",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 41764308.03854598,
            "range": "± 339",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 125648895.37777779,
            "range": "± 2097727",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17555536.293919515,
            "range": "± 140154",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 26068488.201889284,
            "range": "± 517850",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 54175992.44097511,
            "range": "± 849",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 136713599.96521738,
            "range": "± 103797",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 19750760,
            "range": "± 107643",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 34110174.4,
            "range": "± 135672",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 73561907.2,
            "range": "± 78549",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174747169.6,
            "range": "± 198655",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8e6e8b927878291a2ade460fd4d80087f7bc1020",
          "message": "Merge pull request #4 from TBSten/claude/project-thread-1ufbp6\n\ndocs: 公開サイトの root を最新リリースにし、main の書きかけは /dev/（v0.4-dev）に出す（KATACHI-149）",
          "timestamp": "2026-11-06T02:57:17+09:00",
          "tree_id": "921c9f5d82a897efbf162b15e5fe218fe81f4a66",
          "url": "https://github.com/TBSten/katachi/commit/8e6e8b927878291a2ade460fd4d80087f7bc1020"
        },
        "date": 1793902361752,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4957886.175034853,
            "range": "± 3424",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52266327.49496431,
            "range": "± 24682",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 6046676.842894624,
            "range": "± 4067",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 21818502.208326526,
            "range": "± 21762",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2354432.673782147,
            "range": "± 142",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7520087.879306463,
            "range": "± 512",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 23334012.072849356,
            "range": "± 519501",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 75285384.74190477,
            "range": "± 5038661",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2214868.3673422392,
            "range": "± 27700",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 7368831.66896042,
            "range": "± 5943",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 23313366.271432344,
            "range": "± 42315",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 74034112.84134726,
            "range": "± 102983",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656797.4025974,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381181.86521745,
            "range": "± 8",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750961,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987751.2,
            "range": "± 25",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 5668372.325215986,
            "range": "± 3410",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 14971199.996427912,
            "range": "± 17837",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 41764331.24210878,
            "range": "± 332",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 126940935.66276923,
            "range": "± 38698",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17682354.48065455,
            "range": "± 9988",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 26477370.28259877,
            "range": "± 215369",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 52955414.7970386,
            "range": "± 209041",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 140150839.68695652,
            "range": "± 243",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 19738096,
            "range": "± 60644",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 34056937.6,
            "range": "± 381895",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 73589364.8,
            "range": "± 186509",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 174692024,
            "range": "± 457434",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "33dc778be1043d3ca5e1b59a060f03866c6d52be",
          "message": "Merge pull request #5 from TBSten/claude/project-thread-xczyg5\n\nperf(dsl): Glob.compile をパターン文字列で memo 化し、Nearby の候補を最初の違反まで遅延する（KATACHI-236, KATACHI-203, KATACHI-209）",
          "timestamp": "2026-11-06T03:12:24+09:00",
          "tree_id": "7f0b3d705769bf0976ff7c973b7ebb4a8ed431de",
          "url": "https://github.com/TBSten/katachi/commit/33dc778be1043d3ca5e1b59a060f03866c6d52be"
        },
        "date": 1793903307627,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4957918.437807289,
            "range": "± 3429",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52258409.25783973,
            "range": "± 12766",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 2994082.4437841685,
            "range": "± 51156",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 18636931.946072593,
            "range": "± 323470",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 755621.7217679333,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2279084.2398491264,
            "range": "± 1225",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6688055.4239851395,
            "range": "± 11618",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21528782.025023676,
            "range": "± 2800",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 636321.2569880276,
            "range": "± 720",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2116084.8423091755,
            "range": "± 14397",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6335863.616353222,
            "range": "± 30822",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21023996.619990848,
            "range": "± 3004",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656793.28319423,
            "range": "± 3",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381151.40626776,
            "range": "± 6",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750879.022222,
            "range": "± 3",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987753.2,
            "range": "± 29",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2634083.1328132646,
            "range": "± 3713",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5393468.511813907,
            "range": "± 123",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13053703.157206543,
            "range": "± 83500",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 37049034.682140954,
            "range": "± 5132",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 14524192.42772375,
            "range": "± 194955",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 17047289.38506845,
            "range": "± 34437",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25183545.178264856,
            "range": "± 514162",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 49844682.25740193,
            "range": "± 1194802",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17427608,
            "range": "± 50109",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31700918.4,
            "range": "± 153420",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 71357547.2,
            "range": "± 72794",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 172451728,
            "range": "± 155914",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8df5ac18f8bd1b357b9054929de7a40d1a3871d9",
          "message": "Merge pull request #7 from TBSten/claude/project-thread-v12slw\n\ndocs: first-architecture の失敗時の出力例を手順 3 の定義にそろえる (KATACHI-193)",
          "timestamp": "2026-11-06T03:31:07+09:00",
          "tree_id": "c07f36437a5c64a8fbbb5ff610a3ecb796b01aa4",
          "url": "https://github.com/TBSten/katachi/commit/8df5ac18f8bd1b357b9054929de7a40d1a3871d9"
        },
        "date": 1793904841451,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4957905.804185166,
            "range": "± 1153",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52245191.86902942,
            "range": "± 12036",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 2995597.6594105503,
            "range": "± 16201",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 18769923.27743519,
            "range": "± 20024",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 756364.9602233239,
            "range": "± 1186",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2280225.558404401,
            "range": "± 612",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6649184.306388586,
            "range": "± 11455",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21468038.018689245,
            "range": "± 95977",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 642760.6265193018,
            "range": "± 9836",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2122991.4066026113,
            "range": "± 3825",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6340508.137693154,
            "range": "± 586",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21099389.944348693,
            "range": "± 121826",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656766.28271957,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381101.82195055,
            "range": "± 5",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750739.7575758,
            "range": "± 35",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777986294.2666674,
            "range": "± 236",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2636289.119360133,
            "range": "± 1286",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5386102.289503663,
            "range": "± 11958",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13105229.303589022,
            "range": "± 1908",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 36917219.80337493,
            "range": "± 3191",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 14634428.58983255,
            "range": "± 103",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 17011892.323216815,
            "range": "± 2445",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25226800.197938003,
            "range": "± 86942",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 50592318.99258956,
            "range": "± 5452",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17432760,
            "range": "± 53953",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31675814.4,
            "range": "± 7237",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 71363860.8,
            "range": "± 70323",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 172458641.6,
            "range": "± 176270",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "85f86fe4a308941598e46897a80cc939962b536a",
          "message": "Merge pull request #11 from TBSten/claude/project-thread-xdku8z\n\nci: pull request ごとに katachiDiff の Markdown レポートをコメントする (KATACHI-242, KATACHI-223)",
          "timestamp": "2026-11-06T18:14:09+09:00",
          "tree_id": "b3b7d745dc6b24a18f23471ea48ea840439f38fd",
          "url": "https://github.com/TBSten/katachi/commit/85f86fe4a308941598e46897a80cc939962b536a"
        },
        "date": 1793957467561,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4958638.708600652,
            "range": "± 2288",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52258428.970198214,
            "range": "± 12828",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3000162.011167153,
            "range": "± 35663",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 18755304.736116834,
            "range": "± 108163",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 754237.7377067048,
            "range": "± 1951",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2277001.945201968,
            "range": "± 4512",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6680803.92671292,
            "range": "± 993",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21448518.573737375,
            "range": "± 3067",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 637704.9690648946,
            "range": "± 631",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2121680.489656575,
            "range": "± 1724",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6328652.32285694,
            "range": "± 19349",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21176611.97197594,
            "range": "± 38984",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656789.7099863,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381149.4712251,
            "range": "± 4",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750878.222222,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987753.2,
            "range": "± 23",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2638578.2033404666,
            "range": "± 2571",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5384488.723423133,
            "range": "± 14237",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13106317.020478439,
            "range": "± 2783",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 36959391.841355935,
            "range": "± 143131",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 14522946.402939558,
            "range": "± 177842",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 17053809.797460563,
            "range": "± 14423",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25394072.339474324,
            "range": "± 177959",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 50412247.06839629,
            "range": "± 290027",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17444809.6,
            "range": "± 31374",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31703921.6,
            "range": "± 139373",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 71365814.4,
            "range": "± 64443",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 172427048,
            "range": "± 247805",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "a3c24ae1f4da23a2f2b58aa9243271ce53577838",
          "message": "Merge pull request #12 from TBSten/claude/project-thread-tlijka\n\nchore(ide-plugin): buildPlugin に -Pkatachi.timestampVersion で日付入りの版を付ける (KATACHI-246, KATACHI-220)",
          "timestamp": "2026-11-06T19:01:33+09:00",
          "tree_id": "2d0be72df1c5146bd317e3ad3ead99c987213b49",
          "url": "https://github.com/TBSten/katachi/commit/a3c24ae1f4da23a2f2b58aa9243271ce53577838"
        },
        "date": 1793960217821,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4959308.6870161835,
            "range": "± 1168",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52264570.626563,
            "range": "± 5438",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3011238.862031279,
            "range": "± 25267",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 18702633.339594685,
            "range": "± 139542",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 754214.2989343874,
            "range": "± 1951",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2270558.9116104282,
            "range": "± 6776",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6568277.827303483,
            "range": "± 141298",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21530290.91395961,
            "range": "± 2553",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 635341.8251340454,
            "range": "± 842",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2117471.955578168,
            "range": "± 9383",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6249467.697418087,
            "range": "± 145978",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21114211.47188406,
            "range": "± 64156",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656798.94385967,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381199.0909091,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750961,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987746.4,
            "range": "± 31",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2636957.99695096,
            "range": "± 687",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5380214.851708552,
            "range": "± 13092",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13087941.657000935,
            "range": "± 31377",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 36961715.295458935,
            "range": "± 64930",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 14521433.31379648,
            "range": "± 181491",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 17046000.833652478,
            "range": "± 31597",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25453079.1381722,
            "range": "± 86947",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 50233702.17837838,
            "range": "± 3577",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17441297.6,
            "range": "± 38765",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31703606.4,
            "range": "± 142480",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 71364097.6,
            "range": "± 68612",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 172354609.6,
            "range": "± 556498",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "093a66a2dc6b47c86ead27bfc7ea7da105826cc6",
          "message": "Merge pull request #10 from TBSten/claude/project-thread-uqnr00\n\nfeat(ide-plugin): KATACHI-220 JetBrains MCP Server に katachi の tool 7 本を足す（KATACHI-230 / 237 / 239）",
          "timestamp": "2026-11-06T19:54:00+09:00",
          "tree_id": "181d890e6fe73ad79c295cc0470e7d63a5bc21e2",
          "url": "https://github.com/TBSten/katachi/commit/093a66a2dc6b47c86ead27bfc7ea7da105826cc6"
        },
        "date": 1793963383824,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4960094.091364531,
            "range": "± 401",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52246064.26088794,
            "range": "± 13624",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3018074.0611962285,
            "range": "± 17983",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 18783355.835467048,
            "range": "± 52172",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 753018.7818460929,
            "range": "± 19",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2286523.469399347,
            "range": "± 9371",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6669263.143270372,
            "range": "± 19308",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21413916.14050209,
            "range": "± 19411",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 644914.4346450068,
            "range": "± 10857",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2121692.64454338,
            "range": "± 2748",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6325495.461806318,
            "range": "± 48074",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21101390.000458304,
            "range": "± 122568",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656806.01025455,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381223.0065163,
            "range": "± 15",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751065.6000001,
            "range": "± 6",
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
            "value": 2611366.7500831983,
            "range": "± 41163",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5391113.183039176,
            "range": "± 4083",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13035452.531482937,
            "range": "± 52431",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 36986597.487764604,
            "range": "± 103581",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 14519186.929091971,
            "range": "± 185551",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 16929440.921270706,
            "range": "± 91192",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25507670.74826319,
            "range": "± 889",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 49706452.66730159,
            "range": "± 1419171",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17435155.2,
            "range": "± 60178",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31704838.4,
            "range": "± 142557",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 71359769.6,
            "range": "± 59083",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 172412012.8,
            "range": "± 179857",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "55ec84d62602a114aefa3716e1430412f9189fae",
          "message": "Merge pull request #15 from TBSten/claude/project-thread-ij2ox8\n\ntest(diff): KATACHI-250 パス省略表示の回帰テストを追加",
          "timestamp": "2026-11-06T21:03:16+09:00",
          "tree_id": "2bfefe823d9f2a389d49031cedc6a0440ff2c9af",
          "url": "https://github.com/TBSten/katachi/commit/55ec84d62602a114aefa3716e1430412f9189fae"
        },
        "date": 1793967522254,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4959360.067587607,
            "range": "± 1204",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52252034.90057143,
            "range": "± 8014",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3041138.373372779,
            "range": "± 28230",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 18536471.457285903,
            "range": "± 200954",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 764811.1000032685,
            "range": "± 14681",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2276563.8877513064,
            "range": "± 5240",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6577987.280052268,
            "range": "± 164506",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21450070.704830583,
            "range": "± 2799",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 644902.2422458023,
            "range": "± 10837",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2122502.45686643,
            "range": "± 3996",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6355653.244801492,
            "range": "± 1102",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21153945.111062896,
            "range": "± 2710",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656809.55772725,
            "range": "± 7",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381198.90909094,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135751065.6000001,
            "range": "± 6",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777987751.2,
            "range": "± 25",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2612474.252140049,
            "range": "± 35651",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5383747.532054926,
            "range": "± 7580",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13088248.950518128,
            "range": "± 32236",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 37133527.43973173,
            "range": "± 336019",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 14535939.439957375,
            "range": "± 177540",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 17020258.346269894,
            "range": "± 15102",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25210144.39278018,
            "range": "± 55641",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 50531362.46413539,
            "range": "± 102922",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17443691.2,
            "range": "± 43533",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31719369.6,
            "range": "± 145969",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 71360560,
            "range": "± 41230",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 172349724.8,
            "range": "± 477697",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6b47fe287619f49ceff5ad9b089fc27aaea7cbc3",
          "message": "Merge pull request #17 from TBSten/claude/project-thread-zzokoe\n\nfix: KATACHI-251 katachiDiff の Heat 列を Importance にして GitHub の表崩れを防ぐ（KATACHI-223 / KATACHI-242 の続き）",
          "timestamp": "2026-11-06T21:05:29+09:00",
          "tree_id": "b44b788bfd9f58bf5de28a0abc4dfe06e27f163c",
          "url": "https://github.com/TBSten/katachi/commit/6b47fe287619f49ceff5ad9b089fc27aaea7cbc3"
        },
        "date": 1793968819901,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4958610.196301798,
            "range": "± 276",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52264822.355443254,
            "range": "± 6195",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3040353.11904777,
            "range": "± 6791",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 18770707.69945772,
            "range": "± 17518",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 766768.997415673,
            "range": "± 17804",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2277694.3319141204,
            "range": "± 3421",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6675504.446266772,
            "range": "± 30801",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21316100.263567775,
            "range": "± 68818",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 652364.6062646809,
            "range": "± 1058",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2120597.0449454035,
            "range": "± 27",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6347706.781908693,
            "range": "± 11578",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21111526.827817798,
            "range": "± 64222",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656764.44138737,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381100.347254,
            "range": "± 6",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750717.5151515,
            "range": "± 21",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777986242,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2633976.007742018,
            "range": "± 3794",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5385917.842782438,
            "range": "± 11963",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13105566.305232773,
            "range": "± 2353",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 36982883.5586624,
            "range": "± 104839",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 14522814.51783505,
            "range": "± 177845",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 17028977.292237353,
            "range": "± 24815",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25264028.397708777,
            "range": "± 208987",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 50255718.43925234,
            "range": "± 536319",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17422910.4,
            "range": "± 85521",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31705537.6,
            "range": "± 145972",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 71360148.8,
            "range": "± 80830",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 172476972.8,
            "range": "± 140055",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "af79f115d8e6f8b9047788d431ed0a29353784ba",
          "message": "Merge pull request #16 from TBSten/claude/project-thread-cox6eq\n\ntest(ide-plugin): AutoReloadTest の実時間の余裕を 50 ms から 900 ms に広げ、時刻で検証する (KATACHI-247, KATACHI-245)",
          "timestamp": "2026-11-06T21:44:38+09:00",
          "tree_id": "706db18124f25a3840c9eecbf2d85f3e13c0baa2",
          "url": "https://github.com/TBSten/katachi/commit/af79f115d8e6f8b9047788d431ed0a29353784ba"
        },
        "date": 1793969970396,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4958566.231653855,
            "range": "± 2298",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52250802.26418792,
            "range": "± 4288",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 3013178.9116336466,
            "range": "± 19889",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 18738110.389179043,
            "range": "± 102832",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 754236.9311692897,
            "range": "± 1951",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2277330.229289341,
            "range": "± 4016",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6687942.137011768,
            "range": "± 11385",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21509695.578675248,
            "range": "± 97809",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 633204.8567089911,
            "range": "± 5393",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 2113821.72927229,
            "range": "± 10830",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 6335798.803486612,
            "range": "± 30750",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 21136385.60147143,
            "range": "± 102770",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 123656786.50928695,
            "range": "± 3",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 404381128.7218391,
            "range": "± 5",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 1135750812,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 3777986742.9333334,
            "range": "± 24",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 2632341.163238109,
            "range": "± 260",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 5393425.331709905,
            "range": "± 165",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 13086757.538396731,
            "range": "± 31783",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 37048907.568653844,
            "range": "± 5065",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 14640443.834605316,
            "range": "± 9541",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 16948317.7307804,
            "range": "± 122199",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 25185031.082151726,
            "range": "± 336128",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 50151856.526269026,
            "range": "± 84761",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 17439620.8,
            "range": "± 51459",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 31676172.8,
            "range": "± 9460",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 71349766.4,
            "range": "± 34058",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 172439931.2,
            "range": "± 200405",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6a9c3c66200ea08863a99b0c70cb98b1a97510d2",
          "message": "Merge pull request #14 from TBSten/claude/project-thread-lzuued\n\nfeat: KATACHI-234 .module { } 系の API を削除する（KATACHI-78 の削除、KATACHI-233 の置き換え）",
          "timestamp": "2026-11-06T22:02:03+09:00",
          "tree_id": "7d914b0a21a1f7027e26e6c916b76a54c19656b1",
          "url": "https://github.com/TBSten/katachi/commit/6a9c3c66200ea08863a99b0c70cb98b1a97510d2"
        },
        "date": 1793971222865,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4960078.761853192,
            "range": "± 358",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52267524.889142856,
            "range": "± 8740",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 2195861.056098485,
            "range": "± 18796",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 17445126.719749205,
            "range": "± 20310",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 193553.13822146785,
            "range": "± 2920",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 386878.39937575936,
            "range": "± 1549",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 405097.9653030761,
            "range": "± 10595",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 593119.2377001242,
            "range": "± 2926",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 73588.53762024458,
            "range": "± 1842",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 228369.67496951035,
            "range": "± 2920",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 72404.50458890044,
            "range": "± 45",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 231369.60928935307,
            "range": "± 5737",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17699232.73156067,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 46681510.727178216,
            "range": "± 4",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17567768.38780922,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 46245819.35660298,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1972505.2031652112,
            "range": "± 1180",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3120858.8798256754,
            "range": "± 144",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 3330910.6921998737,
            "range": "± 1313",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 4800276.826625714,
            "range": "± 2939",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16805361.07808884,
            "range": "± 175435",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 23168318.42180874,
            "range": "± 21728",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18262537.666495245,
            "range": "± 1645",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 27169074.642831307,
            "range": "± 4939",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 15103849.6,
            "range": "± 43435",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 30003456,
            "range": "± 116242",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69094062.4,
            "range": "± 26759",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 169933640,
            "range": "± 471085",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "9062763e79a29a9bb7c773405badf8b71ae0f7bb",
          "message": "Merge pull request #21 from TBSten/claude/project-thread-fsvko3\n\nrefactor: KATACHI-76 モジュール capture と modulePlacements の残りを削除する（KATACHI-234 の続き）",
          "timestamp": "2026-11-06T23:42:23+09:00",
          "tree_id": "08de89fd81c05ea953d0c314ba350c08c1d44953",
          "url": "https://github.com/TBSten/katachi/commit/9062763e79a29a9bb7c773405badf8b71ae0f7bb"
        },
        "date": 1793977061790,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4958571.158628494,
            "range": "± 164",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52257580.25558098,
            "range": "± 11617",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 2207403.7876588325,
            "range": "± 11647",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 17388846.05111111,
            "range": "± 250764",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 193452.8864426581,
            "range": "± 249",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 386381.99452179763,
            "range": "± 7401",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 403625.6442664684,
            "range": "± 10250",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 587842.6974103374,
            "range": "± 1453",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 71132.43191353927,
            "range": "± 121",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 228077.36540922004,
            "range": "± 389",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 71948.43804448444,
            "range": "± 1779",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 230385.37648640416,
            "range": "± 5546",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17699229.318919715,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 46681503.33560309,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17567765.493806206,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 46245807.92652491,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1970828.2989880894,
            "range": "± 1205",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3115414.607283861,
            "range": "± 250",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 3327660.0610304577,
            "range": "± 1077",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 4796099.177305622,
            "range": "± 804",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16692710.098166859,
            "range": "± 840",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 23176204.948332287,
            "range": "± 952",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18255465.236875854,
            "range": "± 5963",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 27155697.813573856,
            "range": "± 7083",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 15031648,
            "range": "± 35465",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 29900382.4,
            "range": "± 380669",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69033643.2,
            "range": "± 35296",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 169960320,
            "range": "± 193813",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "ea5a472895ce8a47ee2e17b885e6dc1d3f1f0e59",
          "message": "Merge pull request #19 from TBSten/claude/project-thread-i9r7d2\n\nKATACHI-228 / 229: katachi CLI の全サブコマンドを Kotlin に移し、sh を同梱しない（親 KATACHI-226）",
          "timestamp": "2026-11-06T23:50:19+09:00",
          "tree_id": "bec42e7deec03b0587ab6919f44e286ade44b8a9",
          "url": "https://github.com/TBSten/katachi/commit/ea5a472895ce8a47ee2e17b885e6dc1d3f1f0e59"
        },
        "date": 1793978354967,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4957182.243355485,
            "range": "± 2284",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52266450.74365806,
            "range": "± 24786",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 2176695.438315477,
            "range": "± 8897",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 17441758.592445686,
            "range": "± 308089",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 193392.79928670605,
            "range": "± 344",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 381997.76673737715,
            "range": "± 2021",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 410117.3913156803,
            "range": "± 19",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 606634.4925818484,
            "range": "± 1670",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 72136.38338781138,
            "range": "± 2078",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 229069.20696319704,
            "range": "± 427",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 73176.38882457346,
            "range": "± 178",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 230745.2211851228,
            "range": "± 6579",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17699225.025359828,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 46681492.77213976,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17567761.00305674,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 46245798.572382405,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1971318.3944292949,
            "range": "± 117",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3118259.2717435565,
            "range": "± 3256",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 3328306.4904168216,
            "range": "± 2172",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 4795522.221662277,
            "range": "± 302",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16691189.918708155,
            "range": "± 860",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 23275168.697401702,
            "range": "± 159350",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18258564.64790053,
            "range": "± 768",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 27145303.907794666,
            "range": "± 30164",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 15037089.6,
            "range": "± 43446",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 29939636.8,
            "range": "± 447763",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69041585.6,
            "range": "± 61579",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 169956630.4,
            "range": "± 177682",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "18413b23d66f85999eee02ef7d62f58c7577c2e4",
          "message": "Merge pull request #23 from TBSten/claude/project-thread-7109ud\n\ndocs(katachi-script): InProcess の KDoc から sh 委譲の一文を消す (KATACHI-256, KATACHI-228)",
          "timestamp": "2026-11-07T00:23:01+09:00",
          "tree_id": "0454bfd73137603a77d2970b8e3835ed63949d78",
          "url": "https://github.com/TBSten/katachi/commit/18413b23d66f85999eee02ef7d62f58c7577c2e4"
        },
        "date": 1793979531332,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4958620.862621477,
            "range": "± 307",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52258471.205714285,
            "range": "± 12901",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 2181513.966612184,
            "range": "± 25642",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 17385442.05093617,
            "range": "± 235953",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 191244.88479284145,
            "range": "± 3767",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 382629.880074273,
            "range": "± 6",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 410117.78093161876,
            "range": "± 19",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 597442.7812065501,
            "range": "± 13540",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 71020.42205770758,
            "range": "± 300",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 228749.3479077993,
            "range": "± 83",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 73600.43548943076,
            "range": "± 255",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 227829.35138690696,
            "range": "± 1549",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17699229.39668057,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 46681505.02913574,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17567765.560154244,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 46245808.681004494,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1971266.2724566788,
            "range": "± 125",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3117590.6012548576,
            "range": "± 3224",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 3327943.9914273196,
            "range": "± 957",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 4792643.415976803,
            "range": "± 211",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16689664.209741253,
            "range": "± 4800",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 23167090.06129556,
            "range": "± 16452",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18359967.596761286,
            "range": "± 160704",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 27157263.9570657,
            "range": "± 14632",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 15037736,
            "range": "± 46039",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 29992188.8,
            "range": "± 7421",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69037152,
            "range": "± 51876",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 169835520,
            "range": "± 784629",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6d374367135bafe8e6605764dee5a63af7c906ee",
          "message": "Merge pull request #24 from TBSten/claude/KATACHI-263-diff-fixed-importance-scale\n\nfeat(diff): Importance を PR 内の最大値ではなく固定の基準値で測る (KATACHI-263, KATACHI-261)",
          "timestamp": "2026-11-07T00:58:26+09:00",
          "tree_id": "0b459b270e83a7f73a6d54e34c9493fa03106681",
          "url": "https://github.com/TBSten/katachi/commit/6d374367135bafe8e6605764dee5a63af7c906ee"
        },
        "date": 1793981609265,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4962167.535790524,
            "range": "± 5688",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52258237.68690203,
            "range": "± 12514",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 2189701.877940563,
            "range": "± 18976",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 17356153.259258166,
            "range": "± 52179",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 193664.7964714007,
            "range": "± 778",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 381057.66721396387,
            "range": "± 1594",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 409913.390021529,
            "range": "± 344",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 607666.5099621012,
            "range": "± 4819",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 71208.38273288005,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 228637.23170270305,
            "range": "± 1281",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 71048.38609599441,
            "range": "± 344",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 234329.25192282116,
            "range": "± 739",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17699225.143841553,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 46681495.35749017,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17567761.97175583,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 46245798.77973461,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1971172.9653224244,
            "range": "± 79",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3117792.0339210774,
            "range": "± 4013",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 3329015.9119613348,
            "range": "± 3001",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 4781525.1853519045,
            "range": "± 23535",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16691296.92890599,
            "range": "± 758",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 23163448.85016236,
            "range": "± 19848",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18259218.46033705,
            "range": "± 1247",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 27159670.844746523,
            "range": "± 1323",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 15036880,
            "range": "± 41254",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 29938720,
            "range": "± 453043",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69040545.6,
            "range": "± 37707",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 169901192,
            "range": "± 419104",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "b64e1a4d720b003089be8c1f4588f8e0e57f2a15",
          "message": "Merge pull request #25 from TBSten/claude/KATACHI-101-script-katachi-kts\n\nfeat(cli, ide): *.katachi.kts で katachi のコマンドを前置きなしで書いて実行する（IDE の補完は CLI から定義をもらう）(KATACHI-101, KATACHI-100, KATACHI-256, KATACHI-151)",
          "timestamp": "2026-11-07T01:48:07+09:00",
          "tree_id": "66fa815d94a476d8dc01ce654fd4e20cfcbf7e9c",
          "url": "https://github.com/TBSten/katachi/commit/b64e1a4d720b003089be8c1f4588f8e0e57f2a15"
        },
        "date": 1793984646352,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"200\"} )",
            "value": 4959285.465990181,
            "range": "± 5678",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.parse ( {\"files\":\"2000\"} )",
            "value": 52250954.466078125,
            "range": "± 4750",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"200\"} )",
            "value": 2187193.648848761,
            "range": "± 16819",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.KonsistScopeBench.validateWithKonsist ( {\"files\":\"2000\"} )",
            "value": 17543175.941050638,
            "range": "± 48137",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 193608.895220251,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 380857.875798284,
            "range": "± 1275",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 403205.6548474974,
            "range": "± 9581",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.discoverAndFlatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 596722.7708405481,
            "range": "± 15987",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 71020.43605270403,
            "range": "± 300",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 229069.37806458966,
            "range": "± 427",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 72164.44292651457,
            "range": "± 1434",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.LayoutEvalBench.flatten ( {\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 227853.37279348914,
            "range": "± 1511",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 17699229.458544068,
            "range": "± 0",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 46681504.793480344,
            "range": "± 2",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 17567765.97404289,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 46245808.98975329,
            "range": "± 1",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 1971293.0913303483,
            "range": "± 154",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 3116448.9573452263,
            "range": "± 268",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 3329228.999248334,
            "range": "± 2969",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"1000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 4795619.657373196,
            "range": "± 54",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"5\"} )",
            "value": 16803988.78375337,
            "range": "± 177343",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"10\",\"roles\":\"20\"} )",
            "value": 23178341.202710055,
            "range": "± 3517",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"5\"} )",
            "value": 18259164.693900645,
            "range": "± 1165",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.WalkBench.validate ( {\"files\":\"10000\",\"modules\":\"100\",\"roles\":\"20\"} )",
            "value": 27161567.71050769,
            "range": "± 4112",
            "unit": "B/op",
            "extra": "mode: avgt\nforks: 2"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"200\"} )",
            "value": 15040948.8,
            "range": "± 48517",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.layoutOnly ( {\"files\":\"2000\"} )",
            "value": 29862092.8,
            "range": "± 668326",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"200\"} )",
            "value": 69032827.2,
            "range": "± 70885",
            "unit": "B/op",
            "extra": "mode: ss\nforks: 5"
          },
          {
            "name": "me.tbsten.katachi.benchmark.AssertColdBench.withKonsist ( {\"files\":\"2000\"} )",
            "value": 169919308.8,
            "range": "± 531012",
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
        "date": 1790892751892,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 241.103734,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 34.884932,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 934.5255,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 47.192006,
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
          "id": "4b2841cc6df9593a180a19738f385b414adcbab5",
          "message": "docs(slides): 5分でわかる katachi のスライドに OGP・favicon・description・canonical を足す\n\nサイト本体と同じ og.png と favicon / アイコンを使い、twitter カード、en と ja の hreflang、\ntheme-color も出す。スライドの CSP の img-src に 'self' を足して favicon を読めるようにする。\n英語のタイトルの「(EN)」も外す。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-02T07:05:50+09:00",
          "tree_id": "657adc7128c4b061b35d22f3bf884aba5a9eb623",
          "url": "https://github.com/TBSten/katachi/commit/4b2841cc6df9593a180a19738f385b414adcbab5"
        },
        "date": 1790893828662,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 408.907369,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 75.352339,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1552.700507,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 77.839973,
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
          "id": "20828c85a900679ee16803317244f663450619f8",
          "message": "feat(install): 作業用ディレクトリの直下を HTML 2つと tmp/ だけにし、スクリプトの写しと cache/ を tmp/ の下へ移す\n\n直下には人が開く check-list.html と project-code-base-report.html、エージェントの作業場所の\ntmp/ だけを置く。katachi-install.sh の写しと cache/（lang・version・docs・maven-local・MANIFEST・\n取得したガイド）は tmp/ の下に置く。前の配置（直下に cache/ か katachi-install.sh がある）は、\ninit やサブコマンドを打ったときに tmp/ の下へ移し、MANIFEST の置き場所の列も書き換える。\n手順書（ja / en）、docs/AGENTS.md の表、prerelease の統合テストの手引きも合わせる。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-10-02T09:09:11+09:00",
          "tree_id": "53cc872e29006badbada1659ab9139308a31e75c",
          "url": "https://github.com/TBSten/katachi/commit/20828c85a900679ee16803317244f663450619f8"
        },
        "date": 1790948101863,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 468.106275,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 64.492319,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1531.177133,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 79.669758,
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
          "id": "2f760ae57215ae096a2503c3f7a1dd79a57120bb",
          "message": "docs: README.ja.md から IDE プラグインからの生成の案内を外す\n\n案内先のテンプレートのガイドに IDE プラグインの節が無いため（利用者の修正）。行の折り返しも整えた。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01V1S8xW78Ggb5XF8aU7Ynr3",
          "timestamp": "2026-11-01T13:57:15+09:00",
          "tree_id": "77aca0e56e1b761d35ef9210938743c05683b129",
          "url": "https://github.com/TBSten/katachi/commit/2f760ae57215ae096a2503c3f7a1dd79a57120bb"
        },
        "date": 1793510015887,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 480.234261,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 64.548275,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1577.050357,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 80.485535,
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
          "id": "d742b09b2f1d472ec763b5afd45f9153b77eb541",
          "message": "fix(intellij): テンプレートセット行のメンバの区切りを英語では \", \" にする\n\n区切りの「・」が直書きで、英語の IDE でもツールウィンドウの組の行に\n「Screen・ViewModel」と出ていた。区切りを KatachiStrings に移し、\n英語は \", \"、日本語は「・」にする。英語のプレビュー画像 14 枚を取り直した。\n\nprerelease の 10-4（文言の日英）を試しに回して見つかったもの。\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-04T03:06:17+09:00",
          "tree_id": "67449bd950119e6d542e203083ae8aab572fe708",
          "url": "https://github.com/TBSten/katachi/commit/d742b09b2f1d472ec763b5afd45f9153b77eb541"
        },
        "date": 1793731005077,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 776.936372,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 66.40657,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1575.070508,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 100.564473,
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
          "id": "0733e7bbef3c68c03f77a84b8ed2485256fdae69",
          "message": "Merge branch 'claude/agitated-bell-6592c6' into landing/kdoc-and-deps-fixes\n\n依存更新で赤くなったテスト 4 件の追従（claude/agitated-bell-6592c6）を、\nKDoc の品質チェック（claude/kdoc-quality-check-5b6b64）の上に取り込む。\n\n衝突は katachi-konsist の internal/DeclarationText.kt と KonsistFacts.kt の 2 ファイル。\nどちらも追従側が「context(...) の前のアノテーションを読み直す回避処理」を消した範囲に、\nKDoc の整理が当たったもの。コードは追従側の版を取り、KDoc の整理だけをその上に\nかけ直した（コード行が追従側と一致することを、コメント行と空行を除いた diff で確認）。\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-04T09:41:39+09:00",
          "tree_id": "7329c6f312f036bb3dbf93a98c80555251503897",
          "url": "https://github.com/TBSten/katachi/commit/0733e7bbef3c68c03f77a84b8ed2485256fdae69"
        },
        "date": 1793809195030,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 413.646478,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 53.217871,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1306.993807,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 67.620977,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "376fa42201e665d9ab023f38c466569784a9c502",
          "message": "Merge pull request #1 from TBSten/claude/project-thread-rppq75\n\nfeat(diff): Git 差分から Role / Group の影響範囲レポートを出す katachiDiff (KATACHI-223)",
          "timestamp": "2026-11-06T01:52:45+09:00",
          "tree_id": "a950c4c5da90b3cb42e84f8a7fdb2e07ddc3cb40",
          "url": "https://github.com/TBSten/katachi/commit/376fa42201e665d9ab023f38c466569784a9c502"
        },
        "date": 1793898603946,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 608.180871,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 64.418736,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1620.815416,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 89.874405,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "c8c02ccd5485fcb0724adda825f6797fbafb572c",
          "message": "Merge pull request #2 from TBSten/claude/project-thread-48lio4\n\nfeat(cli): brew で配る katachi コマンドと :katachi-script モジュールを足す",
          "timestamp": "2026-11-06T01:54:24+09:00",
          "tree_id": "5567823e95e30e97b190e1867cc3c12ebc9e6a09",
          "url": "https://github.com/TBSten/katachi/commit/c8c02ccd5485fcb0724adda825f6797fbafb572c"
        },
        "date": 1793899584355,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 286.097708,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 46.69555,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1030.269302,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 60.232158,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "3645feb1ecf5eec967b2f45a81be37b94afbc3b3",
          "message": "Merge pull request #3 from TBSten/claude/project-thread-9q4lmc\n\ndocs: v0.3 のドキュメントを写しにして凍結し、現行の版を v0.4 にする",
          "timestamp": "2026-11-06T02:16:12+09:00",
          "tree_id": "1088e518e17bb2654068ac26bdda297e12fa9410",
          "url": "https://github.com/TBSten/katachi/commit/3645feb1ecf5eec967b2f45a81be37b94afbc3b3"
        },
        "date": 1793900936314,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 469.778934,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 67.900145,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1507.036884,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 84.937398,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8e6e8b927878291a2ade460fd4d80087f7bc1020",
          "message": "Merge pull request #4 from TBSten/claude/project-thread-1ufbp6\n\ndocs: 公開サイトの root を最新リリースにし、main の書きかけは /dev/（v0.4-dev）に出す（KATACHI-149）",
          "timestamp": "2026-11-06T02:57:17+09:00",
          "tree_id": "921c9f5d82a897efbf162b15e5fe218fe81f4a66",
          "url": "https://github.com/TBSten/katachi/commit/8e6e8b927878291a2ade460fd4d80087f7bc1020"
        },
        "date": 1793902363919,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 516.880657,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 82.536335,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1756.107052,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 84.043165,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "33dc778be1043d3ca5e1b59a060f03866c6d52be",
          "message": "Merge pull request #5 from TBSten/claude/project-thread-xczyg5\n\nperf(dsl): Glob.compile をパターン文字列で memo 化し、Nearby の候補を最初の違反まで遅延する（KATACHI-236, KATACHI-203, KATACHI-209）",
          "timestamp": "2026-11-06T03:12:24+09:00",
          "tree_id": "7f0b3d705769bf0976ff7c973b7ebb4a8ed431de",
          "url": "https://github.com/TBSten/katachi/commit/33dc778be1043d3ca5e1b59a060f03866c6d52be"
        },
        "date": 1793903309709,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 515.090996,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 63.260262,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1573.535124,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 67.757194,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8df5ac18f8bd1b357b9054929de7a40d1a3871d9",
          "message": "Merge pull request #7 from TBSten/claude/project-thread-v12slw\n\ndocs: first-architecture の失敗時の出力例を手順 3 の定義にそろえる (KATACHI-193)",
          "timestamp": "2026-11-06T03:31:07+09:00",
          "tree_id": "c07f36437a5c64a8fbbb5ff610a3ecb796b01aa4",
          "url": "https://github.com/TBSten/katachi/commit/8df5ac18f8bd1b357b9054929de7a40d1a3871d9"
        },
        "date": 1793904844010,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 641.190187,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 54.690425,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1237.937542,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 72.423864,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "85f86fe4a308941598e46897a80cc939962b536a",
          "message": "Merge pull request #11 from TBSten/claude/project-thread-xdku8z\n\nci: pull request ごとに katachiDiff の Markdown レポートをコメントする (KATACHI-242, KATACHI-223)",
          "timestamp": "2026-11-06T18:14:09+09:00",
          "tree_id": "b3b7d745dc6b24a18f23471ea48ea840439f38fd",
          "url": "https://github.com/TBSten/katachi/commit/85f86fe4a308941598e46897a80cc939962b536a"
        },
        "date": 1793957469650,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 775.63998,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 78.511569,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1761.464384,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 78.8432,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "a3c24ae1f4da23a2f2b58aa9243271ce53577838",
          "message": "Merge pull request #12 from TBSten/claude/project-thread-tlijka\n\nchore(ide-plugin): buildPlugin に -Pkatachi.timestampVersion で日付入りの版を付ける (KATACHI-246, KATACHI-220)",
          "timestamp": "2026-11-06T19:01:33+09:00",
          "tree_id": "2d0be72df1c5146bd317e3ad3ead99c987213b49",
          "url": "https://github.com/TBSten/katachi/commit/a3c24ae1f4da23a2f2b58aa9243271ce53577838"
        },
        "date": 1793960220632,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 346.953801,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 48.494577,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1170.391264,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 62.036134,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "093a66a2dc6b47c86ead27bfc7ea7da105826cc6",
          "message": "Merge pull request #10 from TBSten/claude/project-thread-uqnr00\n\nfeat(ide-plugin): KATACHI-220 JetBrains MCP Server に katachi の tool 7 本を足す（KATACHI-230 / 237 / 239）",
          "timestamp": "2026-11-06T19:54:00+09:00",
          "tree_id": "181d890e6fe73ad79c295cc0470e7d63a5bc21e2",
          "url": "https://github.com/TBSten/katachi/commit/093a66a2dc6b47c86ead27bfc7ea7da105826cc6"
        },
        "date": 1793963386337,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 465.469791,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 62.930809,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1616.698586,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 68.452037,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "55ec84d62602a114aefa3716e1430412f9189fae",
          "message": "Merge pull request #15 from TBSten/claude/project-thread-ij2ox8\n\ntest(diff): KATACHI-250 パス省略表示の回帰テストを追加",
          "timestamp": "2026-11-06T21:03:16+09:00",
          "tree_id": "2bfefe823d9f2a389d49031cedc6a0440ff2c9af",
          "url": "https://github.com/TBSten/katachi/commit/55ec84d62602a114aefa3716e1430412f9189fae"
        },
        "date": 1793967524673,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 380.725867,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 56.62712,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1329.290461,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 64.632061,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6b47fe287619f49ceff5ad9b089fc27aaea7cbc3",
          "message": "Merge pull request #17 from TBSten/claude/project-thread-zzokoe\n\nfix: KATACHI-251 katachiDiff の Heat 列を Importance にして GitHub の表崩れを防ぐ（KATACHI-223 / KATACHI-242 の続き）",
          "timestamp": "2026-11-06T21:05:29+09:00",
          "tree_id": "b44b788bfd9f58bf5de28a0abc4dfe06e27f163c",
          "url": "https://github.com/TBSten/katachi/commit/6b47fe287619f49ceff5ad9b089fc27aaea7cbc3"
        },
        "date": 1793968822240,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 406.868649,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 56.073027,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1453.453258,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 68.82707,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "af79f115d8e6f8b9047788d431ed0a29353784ba",
          "message": "Merge pull request #16 from TBSten/claude/project-thread-cox6eq\n\ntest(ide-plugin): AutoReloadTest の実時間の余裕を 50 ms から 900 ms に広げ、時刻で検証する (KATACHI-247, KATACHI-245)",
          "timestamp": "2026-11-06T21:44:38+09:00",
          "tree_id": "706db18124f25a3840c9eecbf2d85f3e13c0baa2",
          "url": "https://github.com/TBSten/katachi/commit/af79f115d8e6f8b9047788d431ed0a29353784ba"
        },
        "date": 1793969972338,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 473.939235,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 69.866221,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1593.692278,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 82.085102,
            "unit": "ms",
            "extra": "violations: 106 (layout) / 102 (with Konsist)\nUnexpectedFile: 81, UnexpectedDirectory: 16, MissingDescription: 5, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6a9c3c66200ea08863a99b0c70cb98b1a97510d2",
          "message": "Merge pull request #14 from TBSten/claude/project-thread-lzuued\n\nfeat: KATACHI-234 .module { } 系の API を削除する（KATACHI-78 の削除、KATACHI-233 の置き換え）",
          "timestamp": "2026-11-06T22:02:03+09:00",
          "tree_id": "7d914b0a21a1f7027e26e6c916b76a54c19656b1",
          "url": "https://github.com/TBSten/katachi/commit/6a9c3c66200ea08863a99b0c70cb98b1a97510d2"
        },
        "date": 1793971225895,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 361.264648,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 45.495079,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1216.385555,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 59.277984,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "9062763e79a29a9bb7c773405badf8b71ae0f7bb",
          "message": "Merge pull request #21 from TBSten/claude/project-thread-fsvko3\n\nrefactor: KATACHI-76 モジュール capture と modulePlacements の残りを削除する（KATACHI-234 の続き）",
          "timestamp": "2026-11-06T23:42:23+09:00",
          "tree_id": "08de89fd81c05ea953d0c314ba350c08c1d44953",
          "url": "https://github.com/TBSten/katachi/commit/9062763e79a29a9bb7c773405badf8b71ae0f7bb"
        },
        "date": 1793977064973,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 506.672619,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 60.249113,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1601.139217,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 67.186386,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "ea5a472895ce8a47ee2e17b885e6dc1d3f1f0e59",
          "message": "Merge pull request #19 from TBSten/claude/project-thread-i9r7d2\n\nKATACHI-228 / 229: katachi CLI の全サブコマンドを Kotlin に移し、sh を同梱しない（親 KATACHI-226）",
          "timestamp": "2026-11-06T23:50:19+09:00",
          "tree_id": "bec42e7deec03b0587ab6919f44e286ade44b8a9",
          "url": "https://github.com/TBSten/katachi/commit/ea5a472895ce8a47ee2e17b885e6dc1d3f1f0e59"
        },
        "date": 1793978357024,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 373.657193,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 55.480523,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1501.496944,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 70.546568,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "18413b23d66f85999eee02ef7d62f58c7577c2e4",
          "message": "Merge pull request #23 from TBSten/claude/project-thread-7109ud\n\ndocs(katachi-script): InProcess の KDoc から sh 委譲の一文を消す (KATACHI-256, KATACHI-228)",
          "timestamp": "2026-11-07T00:23:01+09:00",
          "tree_id": "0454bfd73137603a77d2970b8e3835ed63949d78",
          "url": "https://github.com/TBSten/katachi/commit/18413b23d66f85999eee02ef7d62f58c7577c2e4"
        },
        "date": 1793979533913,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 282.478139,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 37.56374,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 976.836888,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 47.021133,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "81161390+TBSten@users.noreply.github.com",
            "name": "TBSten",
            "username": "TBSten"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6d374367135bafe8e6605764dee5a63af7c906ee",
          "message": "Merge pull request #24 from TBSten/claude/KATACHI-263-diff-fixed-importance-scale\n\nfeat(diff): Importance を PR 内の最大値ではなく固定の基準値で測る (KATACHI-263, KATACHI-261)",
          "timestamp": "2026-11-07T00:58:26+09:00",
          "tree_id": "0b459b270e83a7f73a6d54e34c9493fa03106681",
          "url": "https://github.com/TBSten/katachi/commit/6d374367135bafe8e6605764dee5a63af7c906ee"
        },
        "date": 1793981611317,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "nowinandroid validate() cold",
            "value": 489.222621,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate() warm median",
            "value": 56.573527,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) cold",
            "value": 1656.60969,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          },
          {
            "name": "nowinandroid validate(FileConstraintCheck()) warm median",
            "value": 73.321357,
            "unit": "ms",
            "extra": "violations: 134 (layout) / 130 (with Konsist)\nUnexpectedFile: 130, UncheckedFileConstraint: 4\nwarmups: 5, iterations: 15"
          }
        ]
      }
    ]
  }
}