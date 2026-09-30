window.BENCHMARK_DATA = {
  "lastUpdate": 1790811708378,
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
      }
    ]
  }
}