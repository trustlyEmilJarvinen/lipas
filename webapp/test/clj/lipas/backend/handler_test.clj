(ns lipas.backend.handler-test
  (:require [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest testing is]]
            [jsonista.core :as j]
            [lipas.backend.system :refer [start-system!]]
            [lipas.backend.core :as core]
            [lipas.schema.core :as lipas]
            [ring.mock.request :as mock])
  (:import java.util.Base64))

(def mapper (j/object-mapper {:decode-key-fn keyword}))
(def <-json #(j/read-value % mapper))
(def ->json j/write-value-as-string)

(defn ->base64
  "Encodes a string as base64."
  [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))

(defn basic-auth
  "Creates base64 encoded Authorization header value."
  [user pass]
  (str "Basic " (->base64 (str user ":" pass))))

(defn auth-header
  "Adds Authorization header to the request
  with base64 encoded \"Basic user:pass\" value."
  [req user passwd]
  (mock/header req "Authorization" (basic-auth user passwd)))

(def system (start-system!))
(def db (:db system))
(def app (:app system))

(comment (gen/generate (s/gen ::lipas/email)))
(defn gen-user
  ([]
   (gen-user {:db? false}))
  ([{:keys [db?]}]
   (let [user (gen/generate (s/gen ::lipas/user))]
     (when db?
       (core/add-user db user))
     user)))

(deftest register-user-test
  (let [user (gen-user)
        resp (app (-> (mock/request :post "/actions/register")
                      (mock/content-type "application/json")
                      (mock/body (->json user))))]
    (is (= 201 (:status resp)))))

(deftest register-user-conflict-test
  (let [user (gen-user {:db? true})
        resp (app (-> (mock/request :post "/actions/register")
                      (mock/content-type "application/json")
                      (mock/body (->json user))))
        body (<-json (:body resp))]
    (is (= 409 (:status resp)))
    (is (= "username-conflict" (:type body)))))

(deftest login-failure-test
  (let [resp (app (-> (mock/request :post "/actions/login")
                      (mock/content-type "application/json")
                      (auth-header "this" "fails")))]
    (is (= (:status resp) 401))))

(deftest login-test
  (let [user (gen-user {:db? true})
        resp (app (-> (mock/request :post "/actions/login")
                      (mock/content-type "application/json")
                      (auth-header (:username user) (:password user))))
        body (<-json (:body resp))]
    (is (= 200 (:status resp)))
    (is (= (:email user) (:email body)))))