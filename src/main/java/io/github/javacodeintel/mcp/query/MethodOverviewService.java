package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.CallEdge;
import io.github.javacodeintel.mcp.domain.MethodInfo;
import io.github.javacodeintel.mcp.domain.ResolutionKind;
import io.github.javacodeintel.mcp.ports.IndexStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds a bounded method view without serializing the complete outgoing graph. */
public final class MethodOverviewService {
    private static final List<CallCategory> REPRESENTATIVE_ORDER = List.of(
            CallCategory.DATA_ACCESS, CallCategory.BOM_EXPANSION,
            CallCategory.EXPORT_OR_BATCH, CallCategory.BUSINESS, CallCategory.UNKNOWN);

    private final IndexStore store;
    private final CallEdgeClassifier classifier = new CallEdgeClassifier();

    public MethodOverviewService(IndexStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public MethodOverview overview(String owner, String name, String descriptor, int representativeLimit) throws IOException {
        return overview(owner, name, descriptor, representativeLimit, true);
    }

    public MethodOverview overview(String owner, String name, String descriptor, int representativeLimit, boolean includeCallers) throws IOException {
        if (representativeLimit < 1 || representativeLimit > 50) {
            throw new IllegalArgumentException("representativeLimit must be between 1 and 50");
        }
        List<MethodInfo> methods = store.findMethods(owner, name, descriptor);
        if (methods.isEmpty()) throw new IllegalArgumentException("Method not found: " + owner + "." + name);
        if (methods.size() > 1) throw new IllegalArgumentException("descriptor is required when method is overloaded: " + owner + "." + name);
        MethodInfo method = methods.get(0);

        List<CallEdge> rawCallees = store.findCallees(owner, name, method.descriptor());
        List<CallEdgeView> groupedCallees = new RelationQueryService(store).allCallees(
                owner, name, method.descriptor(), new CalleeQuery(new PageRequest(PageRequest.MAX_LIMIT, 0), CallCategory.ALL, null, null, true));

        EnumMap<CallCategory, Long> counts = new EnumMap<>(CallCategory.class);
        for (CallEdgeView edge : groupedCallees) counts.merge(edge.category(), 1L, Long::sum);
        List<CallCategoryCount> categoryCounts = new ArrayList<>();
        for (CallCategory category : List.of(CallCategory.BUSINESS, CallCategory.DATA_ACCESS, CallCategory.BOM_EXPANSION,
                CallCategory.EXPORT_OR_BATCH, CallCategory.FRAMEWORK_OR_UTILITY, CallCategory.UNKNOWN)) {
            Long count = counts.get(category);
            if (count != null && count > 0) categoryCounts.add(new CallCategoryCount(category, count));
        }

        List<CallEdgeView> representatives = representatives(groupedCallees, representativeLimit);
        List<CallEdgeView> callerExamples = new ArrayList<>();
        if (includeCallers) {
            for (CallEdge edge : store.findCallers(owner, name, method.descriptor(), false, representativeLimit, 0)) {
                callerExamples.add(classifier.classify(edge));
            }
        }
        List<String> warnings = new ArrayList<>();
        if (rawCallees.stream().anyMatch(edge -> edge.resolutionKind() != ResolutionKind.DIRECT)) {
            warnings.add("Call graph contains polymorphic or unresolved edges; it is a static approximation");
        }
        if (groupedCallees.stream().anyMatch(edge -> edge.category() == CallCategory.UNKNOWN)) {
            warnings.add("Some targets could not be assigned a navigation category");
        }
        return new MethodOverview(method, store.countCallers(owner, name, method.descriptor(), false), callerExamples,
                rawCallees.size(), groupedCallees.size(), categoryCounts, representatives, warnings);
    }

    public List<MethodOverview> overviews(String owner, String name, String descriptor, int representativeLimit) throws IOException {
        return overviews(owner, name, descriptor, representativeLimit, true);
    }

    public List<MethodOverview> overviews(String owner, String name, String descriptor, int representativeLimit, boolean includeCallers) throws IOException {
        List<MethodInfo> methods = store.findMethods(owner, name, descriptor);
        List<MethodOverview> result = new ArrayList<>();
        for (MethodInfo method : methods) result.add(overview(owner, name, method.descriptor(), representativeLimit, includeCallers));
        return List.copyOf(result);
    }

    private static List<CallEdgeView> representatives(List<CallEdgeView> grouped, int perCategory) {
        Map<CallCategory, List<CallEdgeView>> byCategory = new EnumMap<>(CallCategory.class);
        for (CallEdgeView view : grouped) {
            if (view.category() == CallCategory.FRAMEWORK_OR_UTILITY) continue;
            byCategory.computeIfAbsent(view.category(), ignored -> new ArrayList<>()).add(view);
        }
        List<CallEdgeView> result = new ArrayList<>();
        for (CallCategory category : REPRESENTATIVE_ORDER) {
            List<CallEdgeView> values = byCategory.getOrDefault(category, List.of());
            values.stream().sorted(Comparator.comparing(MethodOverviewService::targetSignature))
                    .limit(perCategory).forEach(result::add);
            if (result.size() >= perCategory * 2) break;
        }
        return List.copyOf(result.subList(0, Math.min(result.size(), perCategory * 2)));
    }

    private static String targetSignature(CallEdgeView view) {
        CallEdge edge = view.edge();
        return String.valueOf(edge.targetOwner()) + "#" + edge.targetName() + edge.targetDescriptor();
    }
}
