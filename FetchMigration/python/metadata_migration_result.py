from dataclasses import dataclass, field


@dataclass
class MetadataMigrationResult:
    target_doc_count: int = 0
    # Set of indices for which data needs to be migrated
    migration_indices: set = field(default_factory=set)
