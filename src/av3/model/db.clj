(ns av3.model.db
  (:require [clj-http.client :as client]))

(def contas (atom {:saldo 0}))
(def apostas (atom {}))

(defn obter-saldo []
  {:saldo (:saldo @contas)})

(defn depositar [valor]
  (swap! contas update :saldo + valor)
  {:saldo (:saldo @contas)})

(defn registrar-aposta [id-aposta bookmaker market multiplier outcome valor sport]
  (let [current-saldo (:saldo @contas)]
    (cond
      (<= valor 0)
      {:status "erro"
       :message "O valor da aposta deve ser maior que zero."}

      (> valor current-saldo)
      {:status "erro"
       :message "O valor da aposta não pode ser maior que o saldo disponível."}

      (contains? @apostas id-aposta)
      {:status "erro"
       :message "Já existe uma aposta com este ID."}

      :else
      (let [new-aposta {:bookmaker bookmaker
                        :market market
                        :outcome outcome
                        :multiplier multiplier
                        :valor valor
                        :sport sport
                        :status "pendente"}]
        (swap! apostas assoc id-aposta new-aposta)
        (swap! contas update :saldo - valor)
        {:aposta-id id-aposta
         :status "registrada"}))))

(defn consultar-aposta [id-aposta]
  (get @apostas id-aposta))

(defn obter-resultado [id-aposta sport]
  (let [url (str "https://api.the-odds-api.com/v4/sports/" sport "/scores/?apiKey=083e159eb384001b00ba52c8fd8f4513&daysFrom=1&eventIds=" id-aposta)
        response (client/get url {:as :json})]
    (println url)
    (if (= 200 (:status response))
      (first (:body response))
      {:status (:status response) :error "Erro ao obter resultados"})))

(defn determine-winner [resultado]
  (let [{home-name :home_team away-name :away_team scores :scores} resultado
        home-score (Integer/parseInt (get (some #(when (= (:name %) home-name) %) scores) :score "0"))
        away-score (Integer/parseInt (get (some #(when (= (:name %) away-name) %) scores) :score "0"))]
    (cond
      (> home-score away-score) :home
      (< home-score away-score) :away
      :else nil))) ;; nil for draw

(defn liquidar-aposta [id-aposta]
  (let [aposta (get @apostas id-aposta)]
    (if (nil? aposta)
      {:status "erro"
       :message "Aposta não encontrada."}
      (if (= "liquidada" (:status aposta))
        {:status "erro"
         :message "Aposta já está liquidada."}
        (let [sport (:sport aposta)
              resultado (obter-resultado id-aposta sport)
              winning-team (determine-winner resultado)
              outcome (:outcome aposta)]

          (cond
            ;; Check if outcome matches the winning team
            (= winning-team outcome)
            (do
              (swap! apostas update id-aposta assoc :status "liquidada")
              (let [{:keys [valor multiplier]} aposta
                    retorno (* valor multiplier)]
                (swap! contas update :saldo + retorno)
                {:status "sucesso"
                 :message "Aposta liquidada como vencedora."
                 :retorno retorno}))

            ;; Match is completed but the bet lost
            (and (:completed resultado) (not= winning-team outcome))
            (do
              (swap! apostas update id-aposta assoc :status "liquidada")
              {:status "sucesso"
               :message "Aposta liquidada como perdedora."})

            ;; Match not completed or invalid result
            :else
            {:status "erro"
             :message "Resultado inválido ou aposta não completada."}))))))
