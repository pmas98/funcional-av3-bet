(ns av3.service.sports-service
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]))

(def api-key
  (let [key (env :API_KEY)]
    (log/info "Loaded API key:" (if key "Found" "Not found"))
    key))

(defn fetch-sports-data []
  (let [url "https://api.the-odds-api.com/v4/sports"]
    (try
      (http/get url
                {:query-params {"apiKey" "083e159eb384001b00ba52c8fd8f4513"
                                "regions" ["us" "br"]
                                "markets" "h2h"}
                 :as :json
                 :throw-exceptions false})
      (catch Exception e
        (log/error e "Failed to fetch sports data")
        {:status 500
         :body {:error "Failed to fetch sports data"}}))))

(defn buscar-mercados []
  (try
    (let [response (fetch-sports-data)
          status (:status response)
          events (:body response)]
      (if (= 200 status)
        (let [filtered-events (->> events
                                   (filter (fn [event]
                                             (let [sport-key (some-> event :group clojure.string/lower-case)]
                                               (contains? #{"soccer" "basketball"} sport-key)))))]
          {:status 200
           :body (or filtered-events [])})
        {:status status
         :body {:error (str "API Error: " status)}}))
    (catch Exception e
      (log/error e "Error fetching events")
      {:status 500
       :body {:error (str "Internal error: " (.getMessage e))}})))

(defn fetch-event-data [market]
  (let [url (str "https://api.the-odds-api.com/v4/sports/" market "/odds")]
    (try
      (http/get url
                {:query-params {"apiKey" "083e159eb384001b00ba52c8fd8f4513"
                                "regions" "us"
                                "markets" ["h2h" "totals"]}
                 :as :json
                 :throw-exceptions false})
      (catch Exception e
        (log/error e "Failed to fetch events for market:" market)
        {:status 500
         :body {:error "Failed to fetch events for market"}}))))


(defn buscar-eventos [market]
  (try
    (let [response (fetch-event-data market)
          status (:status response)
          events (:body response)]
      (if (= 200 status)
        {:status 200
         :body (or events [])}  ; Directly return the events
        {:status status
         :body {:error (str "API Error: " status)}}))
    (catch Exception e
      (log/error e "Error fetching events")
      {:status 500
       :body {:error (str "Internal error: " (.getMessage e))}})))