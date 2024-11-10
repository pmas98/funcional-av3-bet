(ns av3.model.db
  (:require [clj-http.client :as client]))

(def contas (atom {:saldo 0}))
(def apostas (atom {}))

(defn obter-saldo []
  {:saldo (:saldo @contas)})

(defn atualizar-saldo [contas novo-saldo]
  (assoc contas :saldo novo-saldo))

(defn depositar [valor]
  (swap! contas update :saldo + valor)
  (obter-saldo))

(defn validar-nova-aposta [id-aposta valor saldo]
  (cond
    (<= valor 0) {:status "erro" :message "O valor da aposta deve ser maior que zero."}
    (> valor saldo) {:status "erro" :message "O valor da aposta não pode ser maior que o saldo disponível."}
    (contains? @apostas id-aposta) {:status "erro" :message "Já existe uma aposta com este ID."}
    :else nil))

(defn criar-nova-aposta [id-aposta bookmaker market multiplier outcome valor sport]
  {:bookmaker bookmaker
   :market market
   :outcome outcome
   :multiplier multiplier
   :valor valor
   :sport sport
   :status "pendente"})

(defn registrar-aposta [id-aposta bookmaker market multiplier outcome valor sport]
  (let [current-saldo (:saldo @contas)
        erro (validar-nova-aposta id-aposta valor current-saldo)]
    (if erro
      erro
      (let [nova-aposta (criar-nova-aposta id-aposta bookmaker market multiplier outcome valor sport)]
        (swap! apostas assoc id-aposta nova-aposta)
        (swap! contas update :saldo - valor)
        {:aposta-id id-aposta :status "registrada"}))))

(defn consultar-aposta [id-aposta]
  (get @apostas id-aposta))

(defn obter-resultado [id-aposta sport api-key]
  (let [url (str "https://api.the-odds-api.com/v4/sports/" sport "/scores/?apiKey=" api-key "&daysFrom=2&eventIds=" id-aposta)
        response (client/get url {:as :json})]
    (if (= 200 (:status response))
      (first (:body response))
      {:status (:status response) :error "Erro ao obter resultados"})))

(defn parse-score [scores team-name]
  (let [score (some->> scores
                       (some #(when (= (:name %) team-name) (:score %))))]
    (if score
      (Integer/parseInt (str score)) 
      0)))                           


(defn determine-winner [resultado]
  (let [{:keys [home_team away_team scores]} resultado
        home-score (parse-score scores home_team)
        away-score (parse-score scores away_team)]
    (cond
      (> home-score away-score) home_team
      (< home-score away-score) away_team
      :else nil)))

(defn parse-total-threshold [outcome]
  (let [[_ type threshold] (re-find #"(?i)(Over|Under) (\d+(\.\d+)?)" outcome)]
    (when type
      {:type type, :threshold (Double/parseDouble threshold)})))

(defn calculate-total-score [resultado]
  (let [{:keys [home_team away_team scores]} resultado
        home-score (or (parse-score scores home_team) 0)
        away-score (or (parse-score scores away_team) 0)]
    (println "Home Team Score:" home-score "Away Team Score:" away-score)
    (+ home-score away-score)))


(defn process-winning-bet [id-aposta valor multiplier]
  (let [retorno (* valor multiplier)]
    (swap! apostas update id-aposta assoc :status "liquidada")
    (swap! contas update :saldo + retorno)
    {:status "sucesso" :message "Aposta liquidada como vencedora." :retorno retorno}))

(defn process-losing-bet [id-aposta]
  (swap! apostas update id-aposta assoc :status "liquidada")
  {:status "sucesso" :message "Aposta liquidada como perdedora."})

(defn liquidar-aposta [id-aposta]
  (let [aposta (get @apostas id-aposta)]
    (cond
      (nil? aposta) {:status "erro" :message "Aposta não encontrada."}
      (= "liquidada" (:status aposta)) {:status "erro" :message "Aposta já está liquidada."}
      :else
      (let [{:keys [sport outcome market valor multiplier]} aposta
            resultado (obter-resultado id-aposta sport "083e159eb384001b00ba52c8fd8f4513")
            total-threshold (parse-total-threshold outcome)
            total-score (calculate-total-score resultado)]
        (println resultado)
        (println "Total-threshold:" total-threshold)
        (case market
          "h2h" (if (= (determine-winner resultado) outcome)
                  (process-winning-bet id-aposta valor multiplier)
                  (process-losing-bet id-aposta))

          "totals" (if-let [{:keys [type threshold]} total-threshold]
                     (let [won? (case type
                                  "Over" (> total-score threshold)
                                  "Under" (< total-score threshold)
                                  false)]
                       (if won? (process-winning-bet id-aposta valor multiplier)
                           (process-losing-bet id-aposta)))
                     {:status "erro" :message "Threshold inválido para aposta de totals."})

          {:status "erro" :message "Tipo de mercado não suportado."})))))
