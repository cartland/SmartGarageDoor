/*
 * Copyright 2026 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chriscartland.garage.domain.graph

/**
 * Declares a graph INPUT node in place (docs/DATA_GRAPH_PLAN.md §6):
 * the annotated `StateFlow` property (or cold flow function) IS the
 * node, and [cadence] states the one fact code cannot express — what
 * makes the value change. Everything else about the node is derivable
 * from sources and extracted by `DataGraphExtractionKonsistTest`:
 * edges from the flow expressions feeding `stateIn`, sharing from the
 * `SharingStarted` literal, the owner from the declaring type.
 *
 * Annotate the CONSUMED declaration — the interface property (or
 * manager property) that UseCases name through their constructor
 * parameter types — never an implementation's `override` (the
 * extractor matches constructor parameter types against the declaring
 * type, and source annotations are not inherited). The only legal
 * homes are `:domain` interfaces and `:usecase` ADR-015 managers —
 * the graph's G0 boundary; the extraction test fails on a placement
 * anywhere else (a `:viewmodel` input cannot exist).
 *
 * [id] defaults to the declaration's own name; set it only where the
 * node's graph name differs from the declaration (e.g. the
 * `watchCompanion` node is declared as `observeWatchAppStatus()`).
 * [DataGraph.Cadence.DERIVED] is not a legal argument — derived-ness
 * is extracted from `stateIn`, never declared; the extractor rejects
 * it.
 *
 * SOURCE retention on purpose: the annotation does not exist in the
 * compiled artifact, so it CANNOT be read at runtime — [DataGraph]'s
 * "line not to cross" is carried by the language here, not by
 * convention. The Konsist extractor reads sources, where SOURCE
 * retention is fully visible.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class NodeCadence(
    val cadence: DataGraph.Cadence,
    val id: String = "",
)
