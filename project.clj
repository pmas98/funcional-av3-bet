(defproject av3 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [ring "1.9.2"]                    ; servidor web
                 [compojure "1.6.2"]               ; roteamento para a API
                 [cheshire "5.10.0"]               ; manipulação de JSON
                 [ring/ring-json "0.5.0"]          ; middleware JSON
                 [ring-cors "0.1.13"]
                 [org.clojure/core.async "1.3.610"] ; processamento assíncrono
                 [clj-http "3.12.3"]
                 [environ "1.2.0"]
                 [org.clojure/tools.logging "1.2.4"]] ; Add logging support
  :plugins [[lein-environ "1.2.0"]]               ; Add environ plugin
  :main ^:skip-aot av3.core
  :target-path "target/%s"
  :profiles {:dev {:env {:API_KEY "083e159eb384001b00ba52c8fd8f4513"}}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  
  :env {:API_KEY "083e159eb384001b00ba52c8fd8f4513"}) ; Add default env variables