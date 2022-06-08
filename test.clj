#!/usr/bin/env bb

(ns test-exercises
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.java.shell :as shell]
    [clojure.java.io :as io]
    [babashka.fs :as fs])
  (:import [java.nio.file Path]
           [java.net URI]))

(defn- as-path
  ^Path [path]
  (if (instance? Path path) path
      (if (instance? URI path)
        (java.nio.file.Paths/get ^URI path)
        (.toPath (io/file path)))))

(defn cwd
  "Returns current working directory as path"
  []
  (as-path (System/getProperty "user.dir")))

(def root (str (cwd) "/clojure/"))
(def test-runner-dir (str (fs/parent (cwd)) "/clojure-test-runner/"))

(defn- ->snake_case [s] (str/replace s \- \_))

(def practice-exercises
  (map #(% "slug")
       (-> (str root "main/config.json")
           slurp
           json/parse-string
           (get "exercises")
           (get "practice"))))

(def concept-exercises
  (map #(% "slug")
       (-> (str root "main/config.json")
           slurp
           json/parse-string
           (get "exercises")
           (get "concept"))))

(defn test-exercise [slug]
  (let [practice? (contains? (set practice-exercises) slug)
        example (if practice?
                  (str root "exercises/practice/" slug "/.meta/src/example.clj")
                  (str root "exercises/concept/" slug "/.meta/exemplar.clj"))
        src (if practice?
              (str root "exercises/practice/" slug "/src/" (->snake_case slug) ".clj")
              (str root "exercises/concept/" slug "/src/" (->snake_case slug) ".clj"))]
    (shell/sh "cp" example src)
    (= "pass" ((json/parse-string
                (:out (shell/sh (str test-runner-dir "test-runner.clj")
                                slug
                                (str root (if practice? "exercises/practice/" "exercises/concept/") slug "/")
                                (str root (if practice? "exercises/practice/" "exercises/concept/") slug "/"))))
               "status"))))

(defn test-exercises! []
  (for [exercise (into practice-exercises concept-exercises)]
    {(keyword exercise) (test-exercise exercise)}))

(let [results (test-exercises!)
      fails (filter #(false? (first (vals %))) results)]
  (prn {:tested (count results)
        :fails fails})
  (System/exit (count fails)))
