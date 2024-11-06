(ns av3.controller.api
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]
            [cheshire.core :as json]
            [av3.model.db :as db]
            [av3.service.sports-service :as sports]
            [clojure.tools.logging :as log]))

(defroutes app-routes
  (GET "/health" []
    {:status 200
     :body {:status "ok"
            :api_key_present (not (nil? sports/api-key))}})

  (GET "/eventos" request
    (let [market (get (:params request) "market")]
      (log/info "Market value:" market)
      (if market
        (let [{:keys [status body]} (sports/buscar-eventos market)]
          (log/info "Received events for market:" market)
          {:status status
           :body (if (= status 200)
                   body
                   {:error body})})
        {:status 400
         :body {:error "Market parameter is required"}})))

  (GET "/mercados" []
    (let [{:keys [status body]} (sports/buscar-mercados)]
      {:status status
       :body (if (= status 200)
               body
               {:error body})}))

  (GET "/saldo" []
    {:status 200
     :body (json/generate-string (db/obter-saldo))})

  (POST "/saldo" {:keys [body]}
    (let [valor (:valor body)]
      {:status 200
       :body (json/generate-string (db/depositar (Integer. valor)))}))

(POST "/apostas/registrar" {:keys [body]}
  (let [{:keys [id bookmaker market outcome multiplier valor sport]} body]
    (if (and id bookmaker market outcome multiplier valor sport)
      (let [result (db/registrar-aposta id bookmaker market multiplier outcome (Integer. valor) sport)]
        {:status (if (= "registrada" (:status result)) 200 400)
         :body (json/generate-string result)})
      {:status 400
       :body {:error "All fields (id, bookmaker, market, outcome, multiplier, valor, sport) are required"}})))


  (GET "/apostas" []
    (let [all-apostas @db/apostas
          formatted-apostas (map (fn [[id aposta]]
                                   (assoc aposta :id id))
                                 all-apostas)]
      {:status 200
       :body (json/generate-string formatted-apostas)}))



  (POST "/apostas/liquidar" {:keys [body]}
    (let [id-aposta (:id-aposta body)]
      {:status 200
       :body (json/generate-string (db/liquidar-aposta id-aposta))}))

  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (wrap-params)))
