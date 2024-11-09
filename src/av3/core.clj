(ns av3.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [av3.controller.api :refer [app]]))

(defn -main []
  (run-jetty app {:port 8000 :join? false}))
