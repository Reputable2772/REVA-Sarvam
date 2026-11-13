{
  description = "FarmRoute AI development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            nodejs_22
            python3
            curl
            jq
            git
            android-tools
            jdk17
          ];

          shellHook = ''
            echo "FarmRoute AI devShell activated"
          '';
        };
      });
}
