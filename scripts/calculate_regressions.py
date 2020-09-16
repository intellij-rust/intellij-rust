import argparse
import dataclasses
import json
from dataclasses import dataclass
from typing import List, Iterable, Optional, Dict


@dataclass(frozen=True)
class Annotation(object):
    filePath: str
    line: int
    column: int
    highlightedText: str
    error: str
    inspectionToolId: Optional[str]

    def __str__(self) -> str:
        if self.inspectionToolId is not None:
            suffix = f" by {self.inspectionToolId}"
        else:
            suffix = ""
        return f"{self.filePath}:{self.line}:{self.column} '{self.highlightedText}' ({self.error}){suffix}"

    @staticmethod
    def from_dict(raw_dict: Dict) -> "Annotation":
        return Annotation(raw_dict["filePath"],
                          raw_dict["line"],
                          raw_dict["column"],
                          raw_dict["highlightedText"],
                          raw_dict["error"],
                          raw_dict.get("inspectionToolId"))


def read_data(path: str) -> List[Annotation]:
    with open(path) as json_file:
        data = json.load(json_file)
    return [Annotation.from_dict(value) for value in data]


def dump_as_json(annotations: Iterable[Annotation], path: str) -> None:
    json_array = [dataclasses.asdict(a) for a in annotations]
    with open(path, mode="w") as file:
        json.dump(json_array, file, indent=4)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", type=str, help="github token")

    args = parser.parse_args()

    # Should be synchronized with `org.rustPerformanceTests.CustomRealProjectAnalysisTest`
    without_changes = set(read_data(f"regressions/{args.name}_without_changes.json"))
    with_changes = set(read_data(f"regressions/{args.name}_with_changes.json"))

    fixed = without_changes - with_changes
    new = with_changes - without_changes

    dump_as_json(fixed, f"regressions/{args.name}_fixed.json")
    dump_as_json(new, f"regressions/{args.name}_new.json")

    print(f"total annotations: {len(without_changes)} without changes, {len(with_changes)} with changes")
    # should be single line (second and subsequent lines are not displayed)
    print(f"::warning file={args.name}:: {len(new)} annotations introduced, {len(fixed)} annotations fixed")
    print()

    print(f"{len(new)} annotations introduced")
    for ann in new:
        print(ann)
    print()
    print(f"{len(fixed)} annotations fixed")
    for ann in fixed:
        print(ann)

    if len(new) > 0:
        raise Exception("New regressions detected")
