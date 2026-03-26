// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract AuraScore is ERC721, Ownable {
    
    struct ScoreData {
        uint256 score;
        uint256 timestamp;
    }

    mapping(address => ScoreData) public userScores;

    event ScoreMinted(address indexed user, uint256 score);

    constructor() ERC721("Aura Score Identity", "AURA") Ownable(msg.sender) {}

    function _update(address to, uint256 tokenId, address auth) internal override returns (address) {
        address from = _ownerOf(tokenId);
        if (from != address(0) && to != address(0)) {
            revert("Aura Scores are Soulbound and cannot be transferred.");
        }
        return super._update(to, tokenId, auth);
    }

    function mintAuraScore(address _user, uint256 _score) public onlyOwner {
        uint256 tokenId = uint256(keccak256(abi.encodePacked(_user, block.timestamp)));
        
        userScores[_user] = ScoreData(_score, block.timestamp);
        _safeMint(_user, tokenId);
        
        emit ScoreMinted(_user, _score);
    }

    function getScore(address _user) public view returns (uint256) {
        return userScores[_user].score;
    }
}