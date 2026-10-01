window.BENCHMARK_DATA = {
  "lastUpdate": 1790817870210,
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
      }
    ]
  }
}